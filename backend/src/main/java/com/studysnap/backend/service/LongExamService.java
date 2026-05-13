package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.LongExamCompleteRequest;
import com.studysnap.backend.dto.LongExamMasteryReportResponse;
import com.studysnap.backend.dto.LongExamProgressRequest;
import com.studysnap.backend.dto.LongExamSessionResponse;
import com.studysnap.backend.dto.LongExamStartRequest;
import com.studysnap.backend.dto.LongExamStartResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.InvalidLongExamDifficultyException;
import com.studysnap.backend.exception.LongExamGenerationFailedException;
import com.studysnap.backend.exception.LongExamSessionNotFoundException;
import com.studysnap.backend.exception.LongExamSessionNotInProgressException;
import com.studysnap.backend.exception.LongExamSessionNotPausableException;
import com.studysnap.backend.exception.StudyPackNotFoundException;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizDeduplicationUtils;
import com.studysnap.backend.util.QuizSessionStateUtils;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class LongExamService {
    private static final String SESSION_STATE_DIFFICULTY = "difficulty";
    private static final String SESSION_STATE_COMPLETED = "completed";
    private static final String SESSION_METADATA_DOMAIN_BREAKDOWN = "domainBreakdown";
    private static final String SESSION_METADATA_WEAK_DOMAINS = "weakDomains";
    private static final String SESSION_METADATA_PERFORMANCE_SUMMARY = "performanceSummary";
    private static final String SESSION_METADATA_SUGGESTED_NEXT_STEP = "suggestedNextStep";
    private static final String DOMAIN_KEY = "domain";
    private static final String TOTAL_QUESTIONS_KEY = "totalQuestions";
    private static final String CORRECT_ANSWERS_KEY = "correctAnswers";
    private static final String ACCURACY_PERCENTAGE_KEY = "accuracyPercentage";
    private static final String UNKNOWN_DOMAIN_LABEL = "Uncategorized";
    private static final String DIFFICULTY_EASY = "easy";
    private static final String DIFFICULTY_MEDIUM = "medium";
    private static final String DIFFICULTY_HARD = "hard";
    private static final String DIFFICULTY_MIXED = "mixed";
    private static final String PERFORMANCE_EXCELLENT = "Excellent";
    private static final String PERFORMANCE_GOOD = "Good";
    private static final String PERFORMANCE_FAIR = "Fair";
    private static final String PERFORMANCE_NEEDS_IMPROVEMENT = "Needs Improvement";
    private static final String SUGGESTED_REVIEW_WEAK_DOMAINS = "Review weak domains and retry";
    private static final String SUGGESTED_HARDER_DIFFICULTY = "Try a harder difficulty";
    private static final String SUGGESTED_REVIEW_ANSWERS = "Review your answers and revisit the source note";
    private static final String LONG_EXAM_FORFEITED_MESSAGE = "Long Exam session forfeited.";
    private static final String ANALYTICS_METADATA_SESSION_ID = "sessionId";
    private static final String ANALYTICS_METADATA_QUESTION_COUNT = "questionCount";
    private static final String ANALYTICS_METADATA_DIFFICULTY = "difficulty";
    private static final String ANALYTICS_METADATA_SCORE_PERCENTAGE = "scorePercentage";
    private static final int WEAK_DOMAIN_ACCURACY_THRESHOLD = 60;
    private static final int FAIR_SCORE_THRESHOLD = 50;
    private static final int GOOD_SCORE_THRESHOLD = 70;
    private static final int EXCELLENT_SCORE_THRESHOLD = 90;
    private static final BigDecimal ZERO_SCORE = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final List<QuickReviewSessionStatus> ACTIVE_STATUSES = List.of(
            QuickReviewSessionStatus.GENERATING,
            QuickReviewSessionStatus.IN_PROGRESS,
            QuickReviewSessionStatus.PAUSED
    );
    private static final List<QuickReviewSessionStatus> OBSERVABLE_STATUSES = List.of(
            QuickReviewSessionStatus.GENERATING,
            QuickReviewSessionStatus.IN_PROGRESS,
            QuickReviewSessionStatus.PAUSED,
            QuickReviewSessionStatus.FAILED
    );

    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final FeatureGateService featureGateService;
    private final AuthService authService;
    private final QuizGenerationService quizGenerationService;
    private final AnalyticsService analyticsService;
    private final StudyPackGenerationContextResolver generationContextResolver;
    private final StudySnapProperties properties;

    public LongExamStartResponse startSession(String studyPackIdRaw, UUID userId, LongExamStartRequest request) {
        authService.requireEmailVerified(userId);
        PlanType planType = subscriptionService.resolvePlan(userId);
        featureGateService.checkFeatureAccess(planType, Feature.LONG_EXAM_SESSION);

        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        StudyPackEntity studyPack = findOwnedStudyPackForGenerationOrThrow(studyPackId, userId);
        QuickReviewSessionEntity existing = quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.LONG_EXAM,
                        ACTIVE_STATUSES
                )
                .orElse(null);
        if (existing != null && (existing.getStatus() == QuickReviewSessionStatus.GENERATING
                || !QuizSessionStateUtils.extractQuiz(existing.getSessionState()).isEmpty())) {
            return buildStartResponse(existing);
        }

        String difficulty = resolveDifficulty(request);
        int questionCount = resolveQuestionCount(userId);
        QuickReviewSessionEntity session = quickReviewSessionRepository.save(buildGeneratingSession(
                userId,
                studyPack,
                difficulty
        ));
        StudyPackGenerationContext generationContext = generationContextResolver.resolveForStudyPack(userId, studyPack);
        List<String> disallowedQuestions = extractQuestionTexts(studyPack.getQuiz());

        try {
            List<QuizItem> generatedQuiz = quizGenerationService.generateLongExam(
                    studyPack.getTitle(),
                    studyPack.getSummary(),
                    getKeyConcepts(studyPack),
                    disallowedQuestions,
                    questionCount,
                    difficulty,
                    generationContext
            );
            List<QuizItem> longExamQuiz = QuizDeduplicationUtils.uniqueQuestions(
                    generatedQuiz,
                    QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(disallowedQuestions)
            );
            if (longExamQuiz.size() != questionCount) {
                throw new LongExamGenerationFailedException();
            }

            markSessionReady(session, longExamQuiz, difficulty);
            QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
            trackAnalytics(userId, AnalyticsEventType.LONG_EXAM_STARTED, saved.getStudyPackId(), Map.of(
                    ANALYTICS_METADATA_SESSION_ID, saved.getId().toString(),
                    ANALYTICS_METADATA_QUESTION_COUNT, longExamQuiz.size(),
                    ANALYTICS_METADATA_DIFFICULTY, difficulty
            ));
            return buildStartResponse(saved);
        } catch (Exception ex) {
            markSessionFailed(session);
            QuickReviewSessionEntity failed = quickReviewSessionRepository.save(session);
            return buildStartResponse(failed);
        }
    }

    @Transactional(readOnly = true)
    public LongExamSessionResponse getSession(UUID sessionId, UUID userId) {
        return buildSessionResponse(findOwnedSessionOrThrow(sessionId, userId));
    }

    @Transactional(readOnly = true)
    public LongExamStartResponse getActiveSession(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        findOwnedStudyPackOrThrow(studyPackId, userId);
        return quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.LONG_EXAM,
                        OBSERVABLE_STATUSES
                )
                .map(this::buildStartResponse)
                .orElse(null);
    }

    public LongExamSessionResponse saveProgress(UUID sessionId, UUID userId, LongExamProgressRequest request) {
        QuickReviewSessionEntity session = findOwnedSessionOrThrow(sessionId, userId);
        assertSessionInProgress(session);

        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        int currentQuestionIndex = Math.max(0, Math.min(request.questionIndex(), Math.max(0, quiz.size() - 1)));
        session.setCurrentQuestionIndex(currentQuestionIndex);
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(
                session.getSessionState(),
                request.questionIndex(),
                request.selectedChoiceIndex()
        ));
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        return buildSessionResponse(saved);
    }

    public LongExamSessionResponse pauseSession(UUID sessionId, UUID userId) {
        QuickReviewSessionEntity session = findOwnedSessionOrThrow(sessionId, userId);
        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            throw new LongExamSessionNotPausableException();
        }

        session.setStatus(QuickReviewSessionStatus.PAUSED);
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        return buildSessionResponse(saved);
    }

    public LongExamSessionResponse resumeSession(UUID sessionId, UUID userId) {
        QuickReviewSessionEntity session = findOwnedSessionOrThrow(sessionId, userId);
        if (session.getStatus() != QuickReviewSessionStatus.PAUSED) {
            throw new LongExamSessionNotInProgressException();
        }

        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        return buildSessionResponse(saved);
    }

    public LongExamMasteryReportResponse completeSession(
            UUID sessionId,
            UUID userId,
            LongExamCompleteRequest request
    ) {
        QuickReviewSessionEntity session = findOwnedSessionOrThrow(sessionId, userId);
        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS
                && session.getStatus() != QuickReviewSessionStatus.PAUSED) {
            throw new LongExamSessionNotInProgressException();
        }

        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        Map<Integer, Integer> selectedChoices = QuizSessionStateUtils.extractSelectedChoiceIndexes(session.getSessionState(), quiz);
        LongExamStatistics statistics = computeStatistics(quiz, selectedChoices);
        BigDecimal scorePercentage = BigDecimal.valueOf(statistics.scorePercentage())
                .setScale(2, RoundingMode.HALF_UP);

        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(quiz.size());
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(quiz.size());
        session.setCorrectAnswers(statistics.correctAnswers());
        session.setScorePercentage(scorePercentage);
        session.setRetryCount(0);
        session.setDurationSeconds(request.durationSeconds());
        session.setCompletedAt(OffsetDateTime.now());
        session.setSessionState(markSessionStateCompleted(session.getSessionState()));
        session.setSessionMetadata(buildMasteryReportMetadata(statistics));

        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        trackAnalytics(userId, AnalyticsEventType.LONG_EXAM_COMPLETED, saved.getStudyPackId(), Map.of(
                ANALYTICS_METADATA_SESSION_ID, saved.getId().toString(),
                ANALYTICS_METADATA_QUESTION_COUNT, statistics.totalQuestions(),
                ANALYTICS_METADATA_SCORE_PERCENTAGE, statistics.scorePercentage()
        ));
        return buildMasteryReportResponse(saved.getId(), statistics);
    }

    public SimpleMessageResponse forfeitSession(UUID sessionId, UUID userId) {
        QuickReviewSessionEntity session = findOwnedSessionOrThrow(sessionId, userId);
        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS
                && session.getStatus() != QuickReviewSessionStatus.PAUSED) {
            throw new LongExamSessionNotInProgressException();
        }

        session.setStatus(QuickReviewSessionStatus.FORFEITED);
        session.setCompletedAt(OffsetDateTime.now());
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        trackAnalytics(userId, AnalyticsEventType.LONG_EXAM_FORFEITED, saved.getStudyPackId(), Map.of(
                ANALYTICS_METADATA_SESSION_ID, saved.getId().toString()
        ));
        return new SimpleMessageResponse(LONG_EXAM_FORFEITED_MESSAGE);
    }

    private QuickReviewSessionEntity findOwnedSessionOrThrow(UUID sessionId, UUID userId) {
        return quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.LONG_EXAM
                )
                .orElseThrow(LongExamSessionNotFoundException::new);
    }

    private int resolveQuestionCount(UUID userId) {
        LearnerLevel learnerLevel = userRepository.findById(userId)
                .map(UserEntity::getLearnerLevel)
                .orElse(null);
        return switch (learnerLevel == null ? LearnerLevel.COLLEGE : learnerLevel) {
            case GRADE_SCHOOL, JUNIOR_HIGH -> properties.getPricing().getLongExamLowTierCount();
            case BOARD_EXAM_REVIEW, PROFESSIONAL -> properties.getPricing().getLongExamHighTierCount();
            case SENIOR_HIGH, COLLEGE, PERSONAL_LEARNING -> properties.getPricing().getLongExamMidTierCount();
        };
    }

    private LongExamStartResponse buildStartResponse(QuickReviewSessionEntity session) {
        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        boolean canResume = session.getStatus() == QuickReviewSessionStatus.IN_PROGRESS
                || session.getStatus() == QuickReviewSessionStatus.PAUSED;
        return new LongExamStartResponse(
                session.getId(),
                session.getStatus().name(),
                canResume ? quiz : List.of(),
                quiz.isEmpty() ? safeTotalQuestions(session) : quiz.size(),
                extractDifficulty(session.getSessionState()),
                canResume
        );
    }

    private LongExamSessionResponse buildSessionResponse(QuickReviewSessionEntity session) {
        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        return new LongExamSessionResponse(
                session.getId(),
                session.getStatus().name(),
                quiz,
                QuizSessionStateUtils.extractSelectedChoiceIndexes(session.getSessionState(), quiz),
                session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex(),
                quiz.isEmpty() ? safeTotalQuestions(session) : quiz.size(),
                extractDifficulty(session.getSessionState()),
                session.getStatus() == QuickReviewSessionStatus.PAUSED
        );
    }

    private QuickReviewSessionEntity buildGeneratingSession(
            UUID userId,
            StudyPackEntity studyPack,
            String difficulty
    ) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPack.getId());
        session.setNoteId(studyPack.getNoteId());
        session.setSessionMode(QuickReviewSessionMode.LONG_EXAM);
        session.setStatus(QuickReviewSessionStatus.GENERATING);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(0);
        session.setCorrectAnswers(0);
        session.setScorePercentage(ZERO_SCORE);
        session.setRetryCount(0);
        session.setSessionMetadata(null);
        session.setSessionState(buildInitialSessionState(difficulty));
        session.setCreatedAt(OffsetDateTime.now());
        session.setCompletedAt(null);
        return session;
    }

    private void markSessionReady(QuickReviewSessionEntity session, List<QuizItem> quiz, String difficulty) {
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(quiz.size());
        session.setCorrectAnswers(0);
        session.setScorePercentage(ZERO_SCORE);
        session.setRetryCount(0);
        session.setSessionMetadata(null);
        session.setSessionState(QuizSessionStateUtils.withQuiz(quiz, buildInitialSessionState(difficulty)));
        session.setCompletedAt(null);
    }

    private void markSessionFailed(QuickReviewSessionEntity session) {
        session.setStatus(QuickReviewSessionStatus.FAILED);
        session.setCurrentQuestionIndex(0);
        session.setTotalQuestions(0);
        session.setCorrectAnswers(0);
        session.setScorePercentage(ZERO_SCORE);
        session.setCompletedAt(null);
    }

    private LongExamStatistics computeStatistics(List<QuizItem> quiz, Map<Integer, Integer> selectedChoices) {
        Map<String, DomainCounter> counters = new LinkedHashMap<>();
        int correctAnswers = 0;
        for (int index = 0; index < quiz.size(); index++) {
            QuizItem item = quiz.get(index);
            String domain = normalizeDomain(item == null ? null : item.concept());
            DomainCounter counter = counters.computeIfAbsent(domain, unused -> new DomainCounter());
            counter.totalQuestions += 1;

            Integer selectedChoiceIndex = selectedChoices.get(index);
            if (item != null && selectedChoiceIndex != null && selectedChoiceIndex.equals(item.correctIndex())) {
                counter.correctAnswers += 1;
                correctAnswers += 1;
            }
        }

        int answeredQuestions = selectedChoices.size();
        int scorePercentage = answeredQuestions <= 0
                ? 0
                : calculateAccuracy(correctAnswers, answeredQuestions);
        List<LongExamMasteryReportResponse.LongExamDomainStat> domainBreakdown = counters.entrySet()
                .stream()
                .map(entry -> new LongExamMasteryReportResponse.LongExamDomainStat(
                        entry.getKey(),
                        entry.getValue().totalQuestions,
                        entry.getValue().correctAnswers,
                        calculateAccuracy(entry.getValue().correctAnswers, entry.getValue().totalQuestions)
                ))
                .toList();
        List<String> weakDomains = domainBreakdown.stream()
                .filter(stat -> stat.accuracyPercentage() < WEAK_DOMAIN_ACCURACY_THRESHOLD)
                .map(LongExamMasteryReportResponse.LongExamDomainStat::domain)
                .toList();
        String performanceSummary = resolvePerformanceSummary(scorePercentage);
        String suggestedNextStep = resolveSuggestedNextStep(scorePercentage, weakDomains);
        return new LongExamStatistics(
                quiz.size(),
                answeredQuestions,
                correctAnswers,
                scorePercentage,
                domainBreakdown,
                weakDomains,
                performanceSummary,
                suggestedNextStep
        );
    }

    private Map<String, Object> buildMasteryReportMetadata(LongExamStatistics statistics) {
        List<Map<String, Object>> domainBreakdown = statistics.domainBreakdown().stream()
                .map(stat -> Map.<String, Object>of(
                        DOMAIN_KEY, stat.domain(),
                        TOTAL_QUESTIONS_KEY, stat.totalQuestions(),
                        CORRECT_ANSWERS_KEY, stat.correctAnswers(),
                        ACCURACY_PERCENTAGE_KEY, stat.accuracyPercentage()
                ))
                .toList();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(SESSION_METADATA_DOMAIN_BREAKDOWN, domainBreakdown);
        metadata.put(SESSION_METADATA_WEAK_DOMAINS, statistics.weakDomains());
        metadata.put(SESSION_METADATA_PERFORMANCE_SUMMARY, statistics.performanceSummary());
        metadata.put(SESSION_METADATA_SUGGESTED_NEXT_STEP, statistics.suggestedNextStep());
        return metadata;
    }

    private LongExamMasteryReportResponse buildMasteryReportResponse(UUID sessionId, LongExamStatistics statistics) {
        return new LongExamMasteryReportResponse(
                sessionId,
                statistics.totalQuestions(),
                statistics.answeredQuestions(),
                statistics.scorePercentage(),
                statistics.domainBreakdown(),
                statistics.weakDomains(),
                statistics.performanceSummary(),
                statistics.suggestedNextStep()
        );
    }

    private StudyPackEntity findOwnedStudyPackForGenerationOrThrow(UUID studyPackId, UUID userId) {
        return studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)
                .orElseThrow(StudyPackNotFoundException::new);
    }

    private StudyPackEntity findOwnedStudyPackOrThrow(UUID studyPackId, UUID userId) {
        return studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(StudyPackNotFoundException::new);
    }

    private Map<String, Object> buildInitialSessionState(String difficulty) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(SESSION_STATE_DIFFICULTY, difficulty);
        state.put(SESSION_STATE_COMPLETED, false);
        return state;
    }

    private Map<String, Object> markSessionStateCompleted(Map<String, Object> existingState) {
        Map<String, Object> nextState = new LinkedHashMap<>();
        if (existingState != null && !existingState.isEmpty()) {
            nextState.putAll(existingState);
        }
        nextState.put(SESSION_STATE_COMPLETED, true);
        return nextState;
    }

    private void assertSessionInProgress(QuickReviewSessionEntity session) {
        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            throw new LongExamSessionNotInProgressException();
        }
    }

    private String resolveDifficulty(LongExamStartRequest request) {
        if (request == null || request.difficulty() == null || request.difficulty().isBlank()) {
            return DIFFICULTY_MEDIUM;
        }
        String normalized = request.difficulty().trim().toLowerCase();
        return switch (normalized) {
            case DIFFICULTY_EASY, DIFFICULTY_MEDIUM, DIFFICULTY_HARD, DIFFICULTY_MIXED -> normalized;
            default -> throw new InvalidLongExamDifficultyException();
        };
    }

    private String extractDifficulty(Map<String, Object> sessionState) {
        if (sessionState == null) {
            return DIFFICULTY_MEDIUM;
        }
        Object raw = sessionState.get(SESSION_STATE_DIFFICULTY);
        if (raw instanceof String difficulty && !difficulty.isBlank()) {
            return difficulty;
        }
        return DIFFICULTY_MEDIUM;
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

    private List<String> getKeyConcepts(StudyPackEntity studyPack) {
        return studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts();
    }

    private String normalizeDomain(String value) {
        if (value == null) {
            return UNKNOWN_DOMAIN_LABEL;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? UNKNOWN_DOMAIN_LABEL : normalized;
    }

    private int calculateAccuracy(int correctAnswers, int totalQuestions) {
        if (totalQuestions <= 0) {
            return 0;
        }
        return (int) Math.round((correctAnswers * 100.0) / totalQuestions);
    }

    private String resolvePerformanceSummary(int scorePercentage) {
        if (scorePercentage >= EXCELLENT_SCORE_THRESHOLD) {
            return PERFORMANCE_EXCELLENT;
        }
        if (scorePercentage >= GOOD_SCORE_THRESHOLD) {
            return PERFORMANCE_GOOD;
        }
        if (scorePercentage >= FAIR_SCORE_THRESHOLD) {
            return PERFORMANCE_FAIR;
        }
        return PERFORMANCE_NEEDS_IMPROVEMENT;
    }

    private String resolveSuggestedNextStep(int scorePercentage, List<String> weakDomains) {
        if (weakDomains != null && !weakDomains.isEmpty()) {
            return SUGGESTED_REVIEW_WEAK_DOMAINS;
        }
        if (scorePercentage >= EXCELLENT_SCORE_THRESHOLD) {
            return SUGGESTED_HARDER_DIFFICULTY;
        }
        return SUGGESTED_REVIEW_ANSWERS;
    }

    private int safeTotalQuestions(QuickReviewSessionEntity session) {
        return session.getTotalQuestions() == null ? 0 : session.getTotalQuestions();
    }

    private void trackAnalytics(
            UUID userId,
            AnalyticsEventType eventType,
            UUID entityId,
            Map<String, Object> metadata
    ) {
        try {
            analyticsService.trackEvent(userId, eventType, entityId, metadata);
        } catch (RuntimeException ignored) {
            // Analytics must never break the primary Long Exam action.
        }
    }

    private static final class DomainCounter {
        private int totalQuestions;
        private int correctAnswers;
    }

    private record LongExamStatistics(
            int totalQuestions,
            int answeredQuestions,
            int correctAnswers,
            int scorePercentage,
            List<LongExamMasteryReportResponse.LongExamDomainStat> domainBreakdown,
            List<String> weakDomains,
            String performanceSummary,
            String suggestedNextStep
    ) {
    }
}
