package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.InterviewPracticeAnswerRequest;
import com.studysnap.backend.dto.InterviewPracticeAnswerResponse;
import com.studysnap.backend.dto.InterviewPracticeStartRequest;
import com.studysnap.backend.dto.InterviewPracticeStartResponse;
import com.studysnap.backend.dto.InterviewReadinessReportResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.InterviewPracticeQuotaExhaustedException;
import com.studysnap.backend.exception.InterviewPracticeSessionNotFoundException;
import com.studysnap.backend.exception.InterviewPracticeSessionNotInProgressException;
import com.studysnap.backend.exception.InvalidInterviewPracticeRequestException;
import com.studysnap.backend.exception.StudyPackNotFoundException;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.service.model.InterviewPracticeCritique;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizDeduplicationUtils;
import com.studysnap.backend.util.QuizSessionStateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class InterviewPracticeService {
    private static final String SUB_MODE_INTERVIEW = "INTERVIEW";
    private static final String AI_RATE_LIMIT_SCOPE = "interview-practice";
    private static final String INTERVIEW_FORFEITED_MESSAGE = "Interview Practice session forfeited.";
    private static final String INTERVIEW_ALREADY_ENDED_MESSAGE = "Interview Practice session has already ended.";
    private static final String ANALYTICS_METADATA_SESSION_ID = "sessionId";
    private static final String ANALYTICS_METADATA_QUESTION_COUNT = "questionCount";
    private static final String ANALYTICS_METADATA_SCORE_PERCENTAGE = "scorePercentage";
    private static final String REPORT_BAND_READY = "READY";
    private static final String REPORT_BAND_ALMOST_READY = "ALMOST_READY";
    private static final String REPORT_BAND_NEEDS_PRACTICE = "NEEDS_PRACTICE";
    private static final int QUESTION_COUNT_SHORT = 5;
    private static final int QUESTION_COUNT_LONG = 10;
    private static final int SOFT_TIMER_SECONDS = 120;
    private static final int READY_THRESHOLD = 80;
    private static final int ALMOST_READY_THRESHOLD = 50;
    private static final BigDecimal ZERO_SCORE = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final List<QuickReviewSessionStatus> ACTIVE_STATUSES = List.of(
            QuickReviewSessionStatus.GENERATING,
            QuickReviewSessionStatus.IN_PROGRESS
    );

    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final QuizGenerationService quizGenerationService;
    private final SubscriptionService subscriptionService;
    private final FeatureGateService featureGateService;
    private final StudySnapProperties properties;
    private final UserUsageService userUsageService;
    private final BillingUsagePeriodService billingUsagePeriodService;
    private final AuthService authService;
    private final AnalyticsService analyticsService;
    private final AiRateLimitService aiRateLimitService;
    private final StudyPackGenerationContextResolver generationContextResolver;

    public InterviewPracticeStartResponse startSession(UUID userId, InterviewPracticeStartRequest request) {
        authService.requireEmailVerified(userId);
        UUID noteId = request == null ? null : request.noteId();
        if (noteId == null) {
            throw new InvalidInterviewPracticeRequestException("A source note is required.");
        }
        int questionCount = normalizeQuestionCount(request.questionCount());
        PlanType planType = subscriptionService.resolvePlan(userId);
        featureGateService.checkFeatureAccess(planType, Feature.INTERVIEW_PRACTICE);

        StudyPackEntity studyPack = studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)
                .orElseThrow(StudyPackNotFoundException::new);
        QuickReviewSessionEntity existing = quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                        userId,
                        studyPack.getId(),
                        QuickReviewSessionMode.ADAPTIVE,
                        ACTIVE_STATUSES
                )
                .orElse(null);
        if (existing != null && isInterviewSession(existing)) {
            return toStartResponse(existing);
        }
        if (existing != null) {
            markForfeited(existing);
            quickReviewSessionRepository.save(existing);
        }

        assertQuotaAvailable(userId, planType);
        aiRateLimitService.assertAllowed(userId, planType, AI_RATE_LIMIT_SCOPE);
        QuickReviewSessionEntity session = quickReviewSessionRepository.save(buildGeneratingSession(userId, studyPack));
        StudyPackGenerationContext generationContext = generationContextResolver.resolveForStudyPack(userId, studyPack);
        List<String> disallowedQuestions = extractQuestionTexts(studyPack.getQuiz());
        try {
            List<QuizItem> generatedQuiz = quizGenerationService.generateInterviewPracticeQuiz(
                    studyPack.getTitle(),
                    studyPack.getSummary(),
                    studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts(),
                    disallowedQuestions,
                    questionCount,
                    generationContext
            );
            List<QuizItem> interviewQuiz = QuizDeduplicationUtils.uniqueQuestions(
                    generatedQuiz,
                    QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(disallowedQuestions)
            );
            if (interviewQuiz.size() != questionCount) {
                throw new InvalidInterviewPracticeRequestException("Could not generate enough unique interview questions.");
            }
            markReady(session, interviewQuiz);
            QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
            userUsageService.incrementInterviewPracticeGeneration(userId, saved.getCreatedAt());
            trackAnalytics(userId, AnalyticsEventType.INTERVIEW_PRACTICE_STARTED, studyPack.getId(), Map.of(
                    ANALYTICS_METADATA_SESSION_ID, saved.getId().toString(),
                    ANALYTICS_METADATA_QUESTION_COUNT, interviewQuiz.size()
            ));
            return toStartResponse(saved);
        } catch (RuntimeException ex) {
            markFailed(session);
            quickReviewSessionRepository.save(session);
            throw ex;
        }
    }

    public InterviewPracticeAnswerResponse answerQuestion(
            UUID sessionId,
            UUID userId,
            InterviewPracticeAnswerRequest request
    ) {
        QuickReviewSessionEntity session = findInterviewSessionOrThrow(sessionId, userId);
        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            throw new InterviewPracticeSessionNotInProgressException();
        }
        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        int questionIndex = request.questionIndex();
        if (questionIndex < 0 || questionIndex >= quiz.size()) {
            throw new InvalidInterviewPracticeRequestException("Question index is invalid.");
        }
        int selectedChoiceIndex = parseChoiceIndex(request.selectedChoice());
        int safeTimeSpentSeconds = Math.max(0, request.timeSpentSeconds());
        session.setSessionState(QuizSessionStateUtils.withInterviewAnswer(
                session.getSessionState(),
                questionIndex,
                selectedChoiceIndex,
                safeTimeSpentSeconds
        ));
        quickReviewSessionRepository.save(session);

        QuizItem question = quiz.get(questionIndex);
        InterviewPracticeCritique critique = quizGenerationService.generateInterviewCritique(question, selectedChoiceIndex);
        session.setSessionState(QuizSessionStateUtils.withInterviewFeedback(
                session.getSessionState(),
                questionIndex,
                Map.of(
                        "verdict", critique.verdict(),
                        "rationale", critique.rationale(),
                        "followUp", critique.followUp()
                )
        ));
        session.setCurrentQuestionIndex(Math.min(questionIndex + 1, quiz.size()));
        quickReviewSessionRepository.save(session);
        QuizItem nextQuestion = questionIndex + 1 < quiz.size() ? quiz.get(questionIndex + 1) : null;
        return new InterviewPracticeAnswerResponse(
                critique.verdict(),
                critique.rationale(),
                critique.followUp(),
                nextQuestion
        );
    }

    public InterviewReadinessReportResponse completeSession(UUID sessionId, UUID userId) {
        QuickReviewSessionEntity session = findInterviewSessionOrThrow(sessionId, userId);
        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            throw new InterviewPracticeSessionNotInProgressException();
        }
        InterviewReadinessReportResponse report = buildReport(session);
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(report.totalQuestions());
        session.setTotalQuestions(report.totalQuestions());
        session.setCorrectAnswers(report.correctAnswers());
        session.setScorePercentage(BigDecimal.valueOf(report.scorePercentage()).setScale(2, RoundingMode.HALF_UP));
        session.setCompletedAt(OffsetDateTime.now());
        quickReviewSessionRepository.save(session);
        trackAnalytics(userId, AnalyticsEventType.INTERVIEW_PRACTICE_COMPLETED, session.getStudyPackId(), Map.of(
                ANALYTICS_METADATA_SESSION_ID, session.getId().toString(),
                ANALYTICS_METADATA_SCORE_PERCENTAGE, report.scorePercentage()
        ));
        return report;
    }

    public SimpleMessageResponse forfeitSession(UUID sessionId, UUID userId) {
        QuickReviewSessionEntity session = findInterviewSessionOrThrow(sessionId, userId);
        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            return new SimpleMessageResponse(INTERVIEW_ALREADY_ENDED_MESSAGE);
        }
        markForfeited(session);
        quickReviewSessionRepository.save(session);
        trackAnalytics(userId, AnalyticsEventType.INTERVIEW_PRACTICE_FORFEITED, session.getStudyPackId(), Map.of(
                ANALYTICS_METADATA_SESSION_ID, session.getId().toString()
        ));
        return new SimpleMessageResponse(INTERVIEW_FORFEITED_MESSAGE);
    }

    private QuickReviewSessionEntity buildGeneratingSession(UUID userId, StudyPackEntity studyPack) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPack.getId());
        session.setNoteId(studyPack.getNoteId());
        session.setSessionMode(QuickReviewSessionMode.ADAPTIVE);
        session.setStatus(QuickReviewSessionStatus.GENERATING);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(0);
        session.setCorrectAnswers(0);
        session.setScorePercentage(ZERO_SCORE);
        session.setRetryCount(0);
        session.setDurationSeconds(null);
        session.setSessionMetadata(null);
        session.setSessionState(QuizSessionStateUtils.withInterviewPracticeState(
                List.of(),
                SUB_MODE_INTERVIEW,
                SOFT_TIMER_SECONDS
        ));
        session.setCreatedAt(OffsetDateTime.now());
        session.setCompletedAt(null);
        return session;
    }

    private void markReady(QuickReviewSessionEntity session, List<QuizItem> quiz) {
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(quiz.size());
        session.setCorrectAnswers(0);
        session.setScorePercentage(ZERO_SCORE);
        session.setRetryCount(0);
        session.setDurationSeconds(null);
        session.setSessionMetadata(null);
        session.setSessionState(QuizSessionStateUtils.withInterviewPracticeState(
                quiz,
                SUB_MODE_INTERVIEW,
                SOFT_TIMER_SECONDS
        ));
        session.setCompletedAt(null);
    }

    private void markFailed(QuickReviewSessionEntity session) {
        session.setStatus(QuickReviewSessionStatus.FAILED);
        session.setTotalQuestions(0);
        session.setCompletedAt(null);
    }

    private void markForfeited(QuickReviewSessionEntity session) {
        session.setStatus(QuickReviewSessionStatus.FORFEITED);
        session.setCompletedAt(OffsetDateTime.now());
    }

    private InterviewPracticeStartResponse toStartResponse(QuickReviewSessionEntity session) {
        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        int currentIndex = session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex();
        QuizItem question = !quiz.isEmpty() && currentIndex < quiz.size() ? quiz.get(currentIndex) : null;
        return new InterviewPracticeStartResponse(
                session.getId(),
                session.getStatus().name(),
                session.getNoteId(),
                session.getStudyPackId(),
                session.getTotalQuestions() == null ? quiz.size() : session.getTotalQuestions(),
                currentIndex,
                SOFT_TIMER_SECONDS,
                question
        );
    }

    private InterviewReadinessReportResponse buildReport(QuickReviewSessionEntity session) {
        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        Map<Integer, Integer> selectedChoices = QuizSessionStateUtils.extractSelectedChoiceIndexes(session.getSessionState(), quiz);
        Map<Integer, Integer> timeSpent = QuizSessionStateUtils.extractInterviewTimeSpentSeconds(session.getSessionState(), quiz);
        Map<String, ConceptCounter> counters = new LinkedHashMap<>();
        LinkedHashSet<String> talkingPoints = new LinkedHashSet<>();
        int correctAnswers = 0;
        for (int index = 0; index < quiz.size(); index++) {
            QuizItem item = quiz.get(index);
            String concept = normalizeConcept(item.concept());
            ConceptCounter counter = counters.computeIfAbsent(concept, ignored -> new ConceptCounter());
            counter.total += 1;
            Integer selected = selectedChoices.get(index);
            boolean correct = selected != null && item.correctIndex() != null && selected.equals(item.correctIndex());
            if (correct) {
                counter.correct += 1;
                correctAnswers += 1;
                talkingPoints.add(concept);
            } else {
                counter.wrong += 1;
            }
        }
        int scorePercentage = quiz.isEmpty() ? 0 : (int) Math.round(correctAnswers * 100.0 / quiz.size());
        String band = scorePercentage >= READY_THRESHOLD
                ? REPORT_BAND_READY
                : scorePercentage >= ALMOST_READY_THRESHOLD ? REPORT_BAND_ALMOST_READY : REPORT_BAND_NEEDS_PRACTICE;
        List<String> strengths = counters.entrySet().stream()
                .filter(entry -> entry.getValue().total > 0 && entry.getValue().wrong == 0)
                .map(Map.Entry::getKey)
                .limit(3)
                .toList();
        List<InterviewReadinessReportResponse.InterviewGap> gaps = counters.entrySet().stream()
                .filter(entry -> entry.getValue().wrong > 0)
                .sorted(Comparator.<Map.Entry<String, ConceptCounter>>comparingInt(entry -> entry.getValue().wrong).reversed())
                .limit(3)
                .map(entry -> new InterviewReadinessReportResponse.InterviewGap(entry.getKey(), session.getNoteId()))
                .toList();
        List<Integer> pacingNotes = timeSpent.entrySet().stream()
                .filter(entry -> entry.getValue() > SOFT_TIMER_SECONDS)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        return new InterviewReadinessReportResponse(
                session.getId(),
                quiz.size(),
                correctAnswers,
                scorePercentage,
                band,
                strengths,
                gaps,
                talkingPoints.stream().limit(3).toList(),
                pacingNotes
        );
    }

    private QuickReviewSessionEntity findInterviewSessionOrThrow(UUID sessionId, UUID userId) {
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.ADAPTIVE
                )
                .orElseThrow(InterviewPracticeSessionNotFoundException::new);
        if (!isInterviewSession(session)) {
            throw new InterviewPracticeSessionNotFoundException();
        }
        return session;
    }

    private boolean isInterviewSession(QuickReviewSessionEntity session) {
        return SUB_MODE_INTERVIEW.equals(QuizSessionStateUtils.extractSubMode(session.getSessionState()));
    }

    private int normalizeQuestionCount(Integer questionCount) {
        if (questionCount == null) {
            return QUESTION_COUNT_SHORT;
        }
        if (questionCount == QUESTION_COUNT_SHORT || questionCount == QUESTION_COUNT_LONG) {
            return questionCount;
        }
        throw new InvalidInterviewPracticeRequestException("Question count must be 5 or 10.");
    }

    private int parseChoiceIndex(String selectedChoice) {
        if (selectedChoice == null || selectedChoice.isBlank()) {
            throw new InvalidInterviewPracticeRequestException("Selected choice is required.");
        }
        return switch (selectedChoice.trim().toUpperCase()) {
            case "A" -> 0;
            case "B" -> 1;
            case "C" -> 2;
            case "D" -> 3;
            default -> throw new InvalidInterviewPracticeRequestException("Selected choice must be A, B, C, or D.");
        };
    }

    private void assertQuotaAvailable(UUID userId, PlanType planType) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        BillingUsagePeriodService.UsagePeriod usagePeriod = billingUsagePeriodService.resolveUsagePeriod(userId, now);
        int monthlyLimit = properties.getPricing().resolveMonthlyInterviewPracticeLimit(planType);
        int usedThisMonth = userUsageService.getMonthlyUsage(userId, now).interviewPracticeUsedThisMonth();
        if (monthlyLimit > 0 && usedThisMonth < monthlyLimit) {
            return;
        }
        trackAnalytics(userId, AnalyticsEventType.INTERVIEW_PRACTICE_QUOTA_EXHAUSTED, null, Map.of(
                "periodStart", usagePeriod.periodStart().toString(),
                "monthlyLimit", monthlyLimit
        ));
        throw new InterviewPracticeQuotaExhaustedException();
    }

    private List<String> extractQuestionTexts(List<QuizItem> quiz) {
        if (quiz == null || quiz.isEmpty()) {
            return List.of();
        }
        return quiz.stream()
                .map(QuizItem::question)
                .filter(question -> question != null && !question.isBlank())
                .toList();
    }

    private String normalizeConcept(String concept) {
        return concept == null || concept.isBlank() ? "Interview judgment" : concept.trim();
    }

    private void trackAnalytics(UUID userId, AnalyticsEventType eventType, UUID studyPackId, Map<String, Object> metadata) {
        try {
            analyticsService.trackEvent(userId, eventType, studyPackId, metadata);
        } catch (RuntimeException ignored) {
            // Analytics must never block Interview Practice.
        }
    }

    private static final class ConceptCounter {
        private int total;
        private int correct;
        private int wrong;
    }
}
