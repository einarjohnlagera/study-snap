package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ChallengeQuizCompleteRequest;
import com.studysnap.backend.dto.GenerateMoreChallengeQuizResponse;
import com.studysnap.backend.exception.NotEnoughNewQuestionsException;
import com.studysnap.backend.dto.ChallengeQuizConceptStatResponse;
import com.studysnap.backend.dto.ChallengeQuizPerformanceSummaryResponse;
import com.studysnap.backend.dto.ChallengeQuizProgressRequest;
import com.studysnap.backend.dto.ChallengeQuizSessionResponse;
import com.studysnap.backend.dto.ChallengeQuizSessionSummaryResponse;
import com.studysnap.backend.dto.ChallengeQuizStartRequest;
import com.studysnap.backend.dto.ChallengeQuizStartResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.QuizSessionReviewResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.ChallengeQuizGenerationFailedException;
import com.studysnap.backend.exception.ChallengeQuizNotAvailableException;
import com.studysnap.backend.exception.ChallengeQuizSessionNotFoundException;
import com.studysnap.backend.exception.ChallengeQuizSessionNotInProgressException;
import com.studysnap.backend.exception.InvalidChallengeQuizDifficultyException;
import com.studysnap.backend.exception.InvalidChallengeQuizModeException;
import com.studysnap.backend.exception.InvalidChallengeQuizResultException;
import com.studysnap.backend.exception.MonthlyBoardExamLimitReachedException;
import com.studysnap.backend.exception.MonthlyChallengeQuizLimitReachedException;
import com.studysnap.backend.exception.StudyPackNotFoundException;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizDeduplicationUtils;
import com.studysnap.backend.util.QuizSessionReviewUtils;
import com.studysnap.backend.util.QuizSessionStateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ChallengeQuizService {
    private static final String SESSION_STATE_TIME_LIMIT_SECONDS = "timeLimitSeconds";
    private static final String SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS = "timerStartedAtEpochSeconds";
    private static final String SESSION_STATE_SELECTED_CHOICES = "selectedChoices";
    private static final String SESSION_STATE_COMPLETED = "completed";
    private static final String SESSION_STATE_DIFFICULTY = "difficulty";
    private static final String SESSION_STATE_MODE = "mode";
    private static final String SESSION_STATE_QUIZ = "quiz";
    private static final String SESSION_METADATA_WEAK_CONCEPTS = "weakConcepts";
    private static final String SESSION_METADATA_CONCEPT_BREAKDOWN = "conceptBreakdown";
    private static final String CONCEPT_KEY = "concept";
    private static final String CORRECT_ANSWERS_KEY = "correctAnswers";
    private static final String TOTAL_QUESTIONS_KEY = "totalQuestions";
    private static final String ACCURACY_PERCENTAGE_KEY = "accuracyPercentage";
    private static final String UNKNOWN_CONCEPT_LABEL = "Uncategorized";
    private static final String AI_RATE_LIMIT_SCOPE = "challenge-quiz";
    private static final String DIFFICULTY_EASY = "easy";
    private static final String DIFFICULTY_MEDIUM = "medium";
    private static final String DIFFICULTY_HARD = "hard";
    private static final String DIFFICULTY_MIXED = "mixed";
    private static final String PERFORMANCE_LEVEL_EXCELLENT = "Excellent";
    private static final String PERFORMANCE_LEVEL_GOOD = "Good";
    private static final String PERFORMANCE_LEVEL_FAIR = "Fair";
    private static final String PERFORMANCE_LEVEL_NEEDS_IMPROVEMENT = "Needs Improvement";
    private static final String ANALYTICS_METADATA_SESSION_ID = "sessionId";
    private static final String ANALYTICS_METADATA_QUESTION_COUNT = "questionCount";
    private static final String ANALYTICS_METADATA_DIFFICULTY = "difficulty";
    private static final String ANALYTICS_METADATA_MODE = "mode";
    private static final String CHALLENGE_QUIZ_SESSION_ALREADY_ENDED_MESSAGE = "Challenge Quiz session has already ended.";
    private static final String CHALLENGE_QUIZ_SESSION_FORFEITED_MESSAGE = "Challenge Quiz session forfeited.";
    private static final String SESSION_REVIEW_NOT_AVAILABLE_CODE = "SESSION_REVIEW_NOT_AVAILABLE";
    private static final String CHALLENGE_QUIZ_SESSION_REVIEW_NOT_AVAILABLE_MESSAGE = "Challenge Quiz session review is only available after completion.";
    private static final String MODE_CHALLENGE = "challenge";
    private static final String MODE_BOARD_EXAM = "board_exam";
    private static final List<QuickReviewSessionStatus> ACTIVE_GENERATION_STATUSES = List.of(
            QuickReviewSessionStatus.GENERATING,
            QuickReviewSessionStatus.IN_PROGRESS
    );
    private static final List<QuickReviewSessionStatus> OBSERVABLE_STATUSES = List.of(
            QuickReviewSessionStatus.GENERATING,
            QuickReviewSessionStatus.IN_PROGRESS,
            QuickReviewSessionStatus.FAILED
    );
    private static final List<QuickReviewSessionStatus> USAGE_COUNTED_STATUSES = List.of(
            QuickReviewSessionStatus.IN_PROGRESS,
            QuickReviewSessionStatus.COMPLETED,
            QuickReviewSessionStatus.FORFEITED
    );
    private static final int FIRST_PAGE = 0;
    private static final int MAX_RECENT_SESSION_LIMIT = 10;
    private static final int WEAK_CONCEPT_ACCURACY_THRESHOLD = 60;
    private static final int LOW_SCORE_THRESHOLD = 50;
    private static final int MID_SCORE_THRESHOLD = 80;
    private static final int HIGH_SCORE_THRESHOLD = 90;
    private static final int LOW_SCORE_QUESTION_COUNT = 10;
    private static final int MID_SCORE_QUESTION_COUNT = 12;
    private static final int HIGH_SCORE_QUESTION_COUNT = 15;
    private static final int INITIAL_CHALLENGE_QUIZ_COUNT = 5;
    private static final int MAX_CHALLENGE_QUIZ_QUESTIONS = 20;
    private static final int GENERATE_MORE_BATCH_SIZE = 5;
    private static final int MIN_NEW_QUESTIONS_AFTER_DEDUP = 3;
    private static final int SECONDS_PER_QUESTION_CHALLENGE = 90;
    private static final int SECONDS_PER_QUESTION_BOARD_EXAM = 60;
    private static final String DEFAULT_SELECTED_DIFFICULTY = DIFFICULTY_MEDIUM;

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
    private final ActivityTrackingService activityTrackingService;
    private final StudyPackGenerationContextResolver generationContextResolver;

    public ChallengeQuizStartResponse startSession(String studyPackIdRaw, UUID userId, ChallengeQuizStartRequest request) {
        authService.requireEmailVerified(userId);
        UUID studyPackId = parseStudyPackId(studyPackIdRaw);
        StudyPackEntity studyPack = findOwnedStudyPackForGenerationOrThrow(studyPackId, userId);
        PlanType planType = subscriptionService.resolvePlan(userId);
        String selectedMode = resolveSelectedMode(request);
        String selectedDifficulty = resolveSelectedDifficulty(planType, selectedMode, request);

        QuickReviewSessionEntity existing = quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.CHALLENGE,
                        ACTIVE_GENERATION_STATUSES
                )
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() == QuickReviewSessionStatus.GENERATING
                    || !QuizSessionStateUtils.extractQuiz(existing.getSessionState()).isEmpty()) {
                int usedThisMonth = resolveUsedThisMonthForResponse(userId, existing);
                return toStartResponse(existing, studyPack, usedThisMonth, planType);
            }
            markSessionForfeited(existing);
            quickReviewSessionRepository.save(existing);
        }

        int usedThisMonth = assertChallengeQuizQuotaAvailable(userId, planType);
        int boardExamUsedThisMonth = 0;
        if (MODE_BOARD_EXAM.equals(selectedMode)) {
            boardExamUsedThisMonth = assertBoardExamQuotaAvailable(userId, planType);
        }
        aiRateLimitService.assertAllowed(userId, planType, AI_RATE_LIMIT_SCOPE);
        ChallengeGenerationProfile profile = resolveGenerationProfile(userId, studyPackId, selectedDifficulty, selectedMode);
        QuickReviewSessionEntity session = quickReviewSessionRepository.save(buildGeneratingSession(
                userId,
                studyPackId,
                studyPack,
                profile.difficulty(),
                selectedMode
        ));
        List<String> disallowedQuestions = extractQuestionTexts(studyPack.getQuiz());
        StudyPackGenerationContext generationContext = buildQuizGenerationContext(userId, studyPack);
        int quizCount = MODE_BOARD_EXAM.equals(selectedMode) ? profile.questionCount() : INITIAL_CHALLENGE_QUIZ_COUNT;
        try {
            List<QuizItem> generatedQuiz = MODE_BOARD_EXAM.equals(selectedMode)
                    ? quizGenerationService.generateBoardExamQuiz(
                            studyPack.getTitle(),
                            studyPack.getSummary(),
                            getKeyConcepts(studyPack),
                            disallowedQuestions,
                            quizCount,
                            profile.difficulty(),
                            generationContext
                    )
                    : quizGenerationService.generateChallengeQuiz(
                            studyPack.getTitle(),
                            studyPack.getSummary(),
                            getKeyConcepts(studyPack),
                            disallowedQuestions,
                            quizCount,
                            profile.difficulty(),
                            generationContext
                    );
            List<QuizItem> challengeQuiz = QuizDeduplicationUtils.uniqueQuestions(
                    generatedQuiz,
                    QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(disallowedQuestions)
            );
            if (challengeQuiz.size() != quizCount) {
                throw new ChallengeQuizGenerationFailedException();
            }

            markSessionReady(session, challengeQuiz, profile.difficulty());
            QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
            userUsageService.incrementChallengeQuizGeneration(userId, saved.getCreatedAt());
            if (MODE_BOARD_EXAM.equals(selectedMode)) {
                userUsageService.incrementBoardExamGeneration(userId, saved.getCreatedAt());
            }
            try {
                analyticsService.trackEvent(userId, AnalyticsEventType.CHALLENGE_QUIZ_STARTED, studyPackId, Map.of(
                        ANALYTICS_METADATA_SESSION_ID, saved.getId().toString(),
                        ANALYTICS_METADATA_QUESTION_COUNT, challengeQuiz.size(),
                        ANALYTICS_METADATA_DIFFICULTY, profile.difficulty(),
                        ANALYTICS_METADATA_MODE, selectedMode
                ));
            } catch (RuntimeException ignored) {
                // Analytics must never turn a successfully generated quiz into a failed session.
            }
            if (MODE_BOARD_EXAM.equals(selectedMode)) {
                return toStartResponse(saved, studyPack, boardExamUsedThisMonth + 1, planType);
            }
            return toStartResponse(saved, studyPack, usedThisMonth + 1, planType);
        } catch (Exception ex) {
            markSessionFailed(session);
            QuickReviewSessionEntity failed = quickReviewSessionRepository.save(session);
            if (MODE_BOARD_EXAM.equals(selectedMode)) {
                return toStartResponse(failed, studyPack, boardExamUsedThisMonth, planType);
            }
            return toStartResponse(failed, studyPack, usedThisMonth, planType);
        }
    }

    @Transactional(readOnly = true)
    public ChallengeQuizStartResponse getInProgressSession(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = parseStudyPackId(studyPackIdRaw);
        StudyPackEntity studyPack = findOwnedStudyPackOrThrow(studyPackId, userId);
        PlanType planType = subscriptionService.resolvePlan(userId);

        return quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.CHALLENGE,
                        OBSERVABLE_STATUSES
                )
                .map(session -> toStartResponse(
                        session,
                        studyPack,
                        resolveUsedThisMonthForResponse(userId, session),
                        planType
                ))
                .orElse(buildEmptyStartResponse(studyPack, (int) countChallengeQuizUsedThisMonth(userId), planType));
    }

    @Transactional(readOnly = true)
    public List<ChallengeQuizSessionSummaryResponse> listRecentSessions(String studyPackIdRaw, UUID userId, int limit) {
        UUID studyPackId = parseStudyPackId(studyPackIdRaw);
        findOwnedStudyPackOrThrow(studyPackId, userId);

        int normalizedLimit = Math.clamp(limit, 1, MAX_RECENT_SESSION_LIMIT);
        return quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.CHALLENGE,
                        PageRequest.of(FIRST_PAGE, normalizedLimit)
                ).stream()
                .map(this::toSessionSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChallengeQuizPerformanceSummaryResponse getPerformanceSummary(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = parseStudyPackId(studyPackIdRaw);
        findOwnedStudyPackOrThrow(studyPackId, userId);

        long attempts = quickReviewSessionRepository.countByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNull(
                userId,
                studyPackId,
                QuickReviewSessionMode.CHALLENGE
        );
        if (attempts == 0) {
            return new ChallengeQuizPerformanceSummaryResponse(null, 0L, null, null, null, List.of());
        }

        BigDecimal bestScore = quickReviewSessionRepository.findBestScorePercentageByUserIdAndStudyPackIdAndSessionMode(
                userId,
                studyPackId,
                QuickReviewSessionMode.CHALLENGE
        );
        QuickReviewSessionEntity latest = quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.CHALLENGE,
                        PageRequest.of(FIRST_PAGE, 1)
                ).stream()
                .findFirst()
                .orElse(null);

        BigDecimal latestScore = latest == null ? null : latest.getScorePercentage();
        return new ChallengeQuizPerformanceSummaryResponse(
                bestScore,
                attempts,
                latestScore,
                latest == null ? null : latest.getCompletedAt(),
                latestScore == null ? null : resolvePerformanceLevel(latestScore),
                latest == null ? List.of() : extractWeakConcepts(latest)
        );
    }

    public ChallengeQuizStartResponse updateSessionProgress(
            String sessionIdRaw,
            UUID userId,
            ChallengeQuizProgressRequest request
    ) {
        QuickReviewSessionEntity session = findChallengeSessionOrThrow(parseSessionId(sessionIdRaw), userId);
        assertSessionInProgress(session);

        int totalQuestions = session.getTotalQuestions() == null ? 0 : session.getTotalQuestions();
        int normalizedIndex = Math.clamp(request.currentQuestionIndex(), 0, Math.max(0, totalQuestions - 1));
        session.setCurrentQuestionIndex(normalizedIndex);
        session.setSessionState(mergeSessionState(session.getSessionState(), request.sessionState()));
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);

        StudyPackEntity studyPack = findOwnedStudyPackOrThrow(saved.getStudyPackId(), userId);
        int usedThisMonth = resolveUsedThisMonthForResponse(userId, saved);
        PlanType planType = subscriptionService.resolvePlan(userId);
        return toStartResponse(saved, studyPack, usedThisMonth, planType);
    }

    public ChallengeQuizSessionResponse completeSession(String sessionIdRaw, UUID userId, ChallengeQuizCompleteRequest request) {
        QuickReviewSessionEntity session = findChallengeSessionForUpdateOrThrow(parseSessionId(sessionIdRaw), userId);
        assertSessionInProgress(session);
        int totalQuestions = session.getTotalQuestions() == null ? request.totalQuestions() : session.getTotalQuestions();
        if (request.correctAnswers() > totalQuestions) {
            throw new InvalidChallengeQuizResultException();
        }

        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        Map<Integer, Integer> selectedChoices = QuizSessionStateUtils.extractSelectedChoiceIndexes(session.getSessionState(), quiz);
        ChallengeStatistics statistics = computeStatistics(
                quiz,
                selectedChoices,
                request.correctAnswers(),
                totalQuestions
        );

        BigDecimal scorePercentage = BigDecimal.valueOf(statistics.correctAnswers())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(statistics.totalQuestions()), 2, RoundingMode.HALF_UP);

        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(statistics.totalQuestions());
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(statistics.totalQuestions());
        session.setCorrectAnswers(statistics.correctAnswers());
        session.setScorePercentage(scorePercentage);
        session.setRetryCount(0);
        session.setDurationSeconds(request.durationSeconds());
        session.setCompletedAt(OffsetDateTime.now());
        session.setSessionState(markSessionStateCompleted(session.getSessionState()));
        session.setSessionMetadata(buildCompletionSessionMetadata(statistics));

        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        activityTrackingService.recordActivity(userId, ActivityType.COMPLETED_CHALLENGE_QUIZ, saved.getStudyPackId());
        return new ChallengeQuizSessionResponse(
                saved.getId().toString(),
                saved.getStudyPackId().toString(),
                saved.getStatus(),
                saved.getTotalQuestions() == null ? 0 : saved.getTotalQuestions(),
                saved.getCorrectAnswers() == null ? 0 : saved.getCorrectAnswers(),
                saved.getScorePercentage() == null ? BigDecimal.ZERO : saved.getScorePercentage(),
                statistics.performanceLevel(),
                statistics.conceptBreakdown(),
                statistics.weakConcepts(),
                saved.getDurationSeconds(),
                saved.getCreatedAt(),
                saved.getCompletedAt()
        );
    }

    public SimpleMessageResponse forfeitSession(String sessionIdRaw, UUID userId) {
        QuickReviewSessionEntity session = findChallengeSessionOrThrow(parseSessionId(sessionIdRaw), userId);
        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            return new SimpleMessageResponse(CHALLENGE_QUIZ_SESSION_ALREADY_ENDED_MESSAGE);
        }

        markSessionForfeited(session);
        quickReviewSessionRepository.save(session);
        return new SimpleMessageResponse(CHALLENGE_QUIZ_SESSION_FORFEITED_MESSAGE);
    }

    public GenerateMoreChallengeQuizResponse generateMoreQuestions(String sessionIdRaw, UUID userId) {
        QuickReviewSessionEntity session = findChallengeSessionForUpdateOrThrow(parseSessionId(sessionIdRaw), userId);
        assertSessionInProgress(session);

        if (MODE_BOARD_EXAM.equals(extractMode(session.getSessionState()))) {
            throw new AppException("BOARD_EXAM_MODE_NOT_SUPPORTED",
                    "Generate more is not available in Board Exam Mode.", org.springframework.http.HttpStatus.CONFLICT);
        }

        List<QuizItem> existingQuiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        if (existingQuiz.size() >= MAX_CHALLENGE_QUIZ_QUESTIONS) {
            throw new AppException("MAX_QUESTIONS_REACHED",
                    "This session has reached the maximum of " + MAX_CHALLENGE_QUIZ_QUESTIONS + " questions.",
                    org.springframework.http.HttpStatus.CONFLICT);
        }

        int batchSize = Math.min(GENERATE_MORE_BATCH_SIZE, MAX_CHALLENGE_QUIZ_QUESTIONS - existingQuiz.size());
        List<String> disallowedQuestions = extractQuestionTexts(existingQuiz);
        List<String> existingConcepts = List.copyOf(extractConcepts(existingQuiz));
        String difficulty = extractDifficulty(session.getSessionState());

        StudyPackEntity studyPack = findOwnedStudyPackOrThrow(session.getStudyPackId(), userId);
        StudyPackGenerationContext generationContext = buildQuizGenerationContext(userId, studyPack);

        List<QuizItem> generated = quizGenerationService.generateMoreChallengeQuiz(
                studyPack.getTitle(),
                studyPack.getSummary(),
                getKeyConcepts(studyPack),
                disallowedQuestions,
                existingConcepts,
                batchSize,
                difficulty,
                generationContext
        );

        List<QuizItem> unique = QuizDeduplicationUtils.uniqueQuestions(
                generated,
                QuizDeduplicationUtils.toNormalizedQuestionSet(existingQuiz)
        );

        if (unique.size() < MIN_NEW_QUESTIONS_AFTER_DEDUP) {
            throw new NotEnoughNewQuestionsException();
        }

        int previousQuizSize = existingQuiz.size();
        Map<String, Object> nextSessionState = QuizSessionStateUtils.appendQuizItems(session.getSessionState(), unique);
        int previousLimit = extractTimeLimitSeconds(session.getSessionState());
        int newTotal = previousQuizSize + unique.size();
        int addedQuestions = newTotal - previousQuizSize;
        int extension = Math.max(0, addedQuestions) * SECONDS_PER_QUESTION_CHALLENGE;
        int newTimeLimitSeconds = previousLimit + extension;
        nextSessionState.put(SESSION_STATE_TIME_LIMIT_SECONDS, newTimeLimitSeconds);
        session.setSessionState(nextSessionState);
        session.setTotalQuestions(newTotal);
        quickReviewSessionRepository.save(session);

        return new GenerateMoreChallengeQuizResponse(
                unique,
                newTotal,
                newTimeLimitSeconds,
                extractTimerStartedAtEpochSeconds(nextSessionState)
        );
    }

    @Transactional(readOnly = true)
    public QuizSessionReviewResponse getSessionReview(String studyPackIdRaw, String sessionIdRaw, UUID userId) {
        UUID studyPackId = parseStudyPackId(studyPackIdRaw);
        findOwnedStudyPackOrThrow(studyPackId, userId);
        QuickReviewSessionEntity session = findChallengeSessionOrThrow(parseSessionId(sessionIdRaw), userId);
        if (!studyPackId.equals(session.getStudyPackId())) {
            throw new ChallengeQuizSessionNotFoundException();
        }
        if (session.getCompletedAt() == null) {
            throw new AppException(
                    SESSION_REVIEW_NOT_AVAILABLE_CODE,
                    CHALLENGE_QUIZ_SESSION_REVIEW_NOT_AVAILABLE_MESSAGE,
                    org.springframework.http.HttpStatus.BAD_REQUEST
            );
        }

        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        Map<Integer, Integer> selectedChoices = QuizSessionStateUtils.extractSelectedChoiceIndexes(session.getSessionState(), quiz);
        List<ChallengeQuizConceptStatResponse> conceptBreakdown = extractConceptBreakdown(session);
        if (conceptBreakdown.isEmpty()) {
            conceptBreakdown = QuizSessionReviewUtils.computeConceptBreakdown(quiz, selectedChoices);
        }
        List<String> weakConcepts = extractWeakConcepts(session);
        if (weakConcepts.isEmpty()) {
            weakConcepts = QuizSessionReviewUtils.computeWeakConcepts(conceptBreakdown);
        }

        return new QuizSessionReviewResponse(
                session.getId().toString(),
                session.getStudyPackId().toString(),
                session.getSessionMode().name(),
                session.getStatus(),
                session.getTotalQuestions() == null ? 0 : session.getTotalQuestions(),
                session.getCorrectAnswers() == null ? 0 : session.getCorrectAnswers(),
                session.getScorePercentage() == null ? BigDecimal.ZERO : session.getScorePercentage(),
                session.getRetryCount() == null ? 0 : session.getRetryCount(),
                session.getDurationSeconds(),
                weakConcepts,
                conceptBreakdown,
                quiz,
                selectedChoices,
                session.getCreatedAt(),
                session.getCompletedAt()
        );
    }

    private int assertChallengeQuizQuotaAvailable(UUID userId, PlanType planType) {
        long usedThisMonth = countChallengeQuizUsedThisMonth(userId);
        int monthlyLimit = properties.getPricing().resolveMonthlyChallengeQuizLimit(planType);
        if (usedThisMonth < monthlyLimit) {
            return (int) usedThisMonth;
        }

        throw new MonthlyChallengeQuizLimitReachedException();
    }

    private long countChallengeQuizUsedThisMonth(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        BillingUsagePeriodService.UsagePeriod usagePeriod = billingUsagePeriodService.resolveUsagePeriod(userId, now);
        long usedFromCountedSessions = quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                QuickReviewSessionMode.CHALLENGE,
                USAGE_COUNTED_STATUSES,
                usagePeriod.periodStart(),
                usagePeriod.periodEnd()
        );
        long usedFromUsage = userUsageService.getMonthlyUsage(userId, now).challengeQuizGenerations();
        return Math.max(usedFromCountedSessions, usedFromUsage);
    }

    private int assertBoardExamQuotaAvailable(UUID userId, PlanType planType) {
        long usedThisMonth = countBoardExamUsedThisMonth(userId);
        int monthlyLimit = properties.getPricing().resolveMonthlyBoardExamLimit(planType);
        if (usedThisMonth < monthlyLimit) {
            return (int) usedThisMonth;
        }

        throw new MonthlyBoardExamLimitReachedException();
    }

    private long countBoardExamUsedThisMonth(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return userUsageService.getMonthlyUsage(userId, now).boardExamUsedThisMonth();
    }

    private int resolveUsedThisMonthForResponse(UUID userId, QuickReviewSessionEntity session) {
        String mode = extractMode(session.getSessionState());
        if (MODE_BOARD_EXAM.equals(mode)) {
            return (int) countBoardExamUsedThisMonth(userId);
        }
        return (int) countChallengeQuizUsedThisMonth(userId);
    }

    private ChallengeGenerationProfile resolveGenerationProfile(
            UUID userId,
            UUID studyPackId,
            String selectedDifficulty,
            String selectedMode
    ) {
        if (MODE_BOARD_EXAM.equals(selectedMode)) {
            return new ChallengeGenerationProfile(MID_SCORE_QUESTION_COUNT, DIFFICULTY_MIXED);
        }
        if (selectedDifficulty != null) {
            return new ChallengeGenerationProfile(resolveQuestionCountForDifficulty(selectedDifficulty), selectedDifficulty);
        }
        QuickReviewSessionEntity latestQuickReview = quickReviewSessionRepository
                .findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        PageRequest.of(0, 1)
                )
                .stream()
                .findFirst()
                .orElse(null);
        BigDecimal previousScore = latestQuickReview == null ? null : latestQuickReview.getScorePercentage();
        if (previousScore == null) {
            return new ChallengeGenerationProfile(MID_SCORE_QUESTION_COUNT, DEFAULT_SELECTED_DIFFICULTY);
        }

        if (previousScore.compareTo(BigDecimal.valueOf(LOW_SCORE_THRESHOLD)) < 0) {
            return new ChallengeGenerationProfile(LOW_SCORE_QUESTION_COUNT, DIFFICULTY_EASY);
        }
        if (previousScore.compareTo(BigDecimal.valueOf(MID_SCORE_THRESHOLD)) < 0) {
            return new ChallengeGenerationProfile(MID_SCORE_QUESTION_COUNT, DEFAULT_SELECTED_DIFFICULTY);
        }
        return new ChallengeGenerationProfile(HIGH_SCORE_QUESTION_COUNT, DIFFICULTY_HARD);
    }

    private int resolveQuestionCountForDifficulty(String difficulty) {
        return switch (difficulty) {
            case DIFFICULTY_EASY -> LOW_SCORE_QUESTION_COUNT;
            case DIFFICULTY_HARD -> HIGH_SCORE_QUESTION_COUNT;
            case DIFFICULTY_MIXED -> MID_SCORE_QUESTION_COUNT;
            default -> MID_SCORE_QUESTION_COUNT;
        };
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

    private Set<String> extractConcepts(List<QuizItem> quiz) {
        if (quiz == null || quiz.isEmpty()) {
            return Set.of();
        }
        return quiz.stream()
                .map(QuizItem::concept)
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private ChallengeQuizStartResponse toStartResponse(
            QuickReviewSessionEntity session,
            StudyPackEntity studyPack,
            int usedThisMonth,
            PlanType planType
    ) {
        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        if (quiz.isEmpty()) {
            if (session.getStatus() != QuickReviewSessionStatus.GENERATING
                    && session.getStatus() != QuickReviewSessionStatus.FAILED) {
                throw new ChallengeQuizNotAvailableException();
            }
        }
        int timeLimitSeconds = extractTimeLimitSeconds(session.getSessionState());
        String mode = extractMode(session.getSessionState());
        int limit = MODE_BOARD_EXAM.equals(mode)
                ? properties.getPricing().resolveMonthlyBoardExamLimit(planType)
                : resolveMonthlyChallengeQuizLimit(planType);
        return new ChallengeQuizStartResponse(
                session.getId().toString(),
                session.getStatus(),
                studyPack.getId().toString(),
                studyPack.getTitle(),
                quiz.size(),
                timeLimitSeconds,
                usedThisMonth,
                limit,
                isDifficultySelectionAvailable(planType),
                mode,
                extractDifficulty(session.getSessionState()),
                quiz,
                session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex(),
                sanitizeSessionStateForClient(session.getSessionState())
        );
    }

    private String resolveSelectedDifficulty(PlanType planType, String selectedMode, ChallengeQuizStartRequest request) {
        if (MODE_BOARD_EXAM.equals(selectedMode)) {
            return DIFFICULTY_MIXED;
        }
        if (request == null || request.difficulty() == null || request.difficulty().isBlank()) {
            return null;
        }
        featureGateService.checkFeatureAccess(planType, Feature.DIFFICULTY_SELECTION);
        String normalized = request.difficulty().trim().toLowerCase();
        return switch (normalized) {
            case DIFFICULTY_EASY, DIFFICULTY_MEDIUM, DIFFICULTY_HARD -> normalized;
            default -> throw new InvalidChallengeQuizDifficultyException();
        };
    }

    private String resolveSelectedMode(ChallengeQuizStartRequest request) {
        if (request == null || request.mode() == null || request.mode().isBlank()) {
            return MODE_CHALLENGE;
        }
        String normalized = request.mode().trim().toLowerCase();
        return switch (normalized) {
            case MODE_CHALLENGE, MODE_BOARD_EXAM -> normalized;
            default -> throw new InvalidChallengeQuizModeException();
        };
    }

    private ChallengeQuizSessionSummaryResponse toSessionSummaryResponse(QuickReviewSessionEntity session) {
        BigDecimal score = session.getScorePercentage() == null ? BigDecimal.ZERO : session.getScorePercentage();
        return new ChallengeQuizSessionSummaryResponse(
                session.getId().toString(),
                session.getTotalQuestions() == null ? 0 : session.getTotalQuestions(),
                session.getCorrectAnswers() == null ? 0 : session.getCorrectAnswers(),
                score,
                resolvePerformanceLevel(score),
                extractConceptBreakdown(session),
                extractWeakConcepts(session),
                session.getCreatedAt(),
                session.getCompletedAt()
        );
    }

    private Map<String, Object> buildInitialSessionState(String difficulty, String mode) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(SESSION_STATE_SELECTED_CHOICES, Map.of());
        state.put(SESSION_STATE_COMPLETED, false);
        state.put(SESSION_STATE_DIFFICULTY, difficulty);
        state.put(SESSION_STATE_MODE, mode);
        return state;
    }

    private String extractMode(Map<String, Object> sessionState) {
        if (sessionState == null) {
            return MODE_CHALLENGE;
        }
        Object raw = sessionState.get(SESSION_STATE_MODE);
        if (raw instanceof String mode && !mode.isBlank()) {
            return mode;
        }
        return MODE_CHALLENGE;
    }

    private String extractDifficulty(Map<String, Object> sessionState) {
        if (sessionState == null) {
            return DEFAULT_SELECTED_DIFFICULTY;
        }
        Object raw = sessionState.get(SESSION_STATE_DIFFICULTY);
        if (raw instanceof String difficulty && !difficulty.isBlank()) {
            return difficulty;
        }
        return DEFAULT_SELECTED_DIFFICULTY;
    }

    private int extractTimeLimitSeconds(Map<String, Object> sessionState) {
        if (sessionState == null) {
            return INITIAL_CHALLENGE_QUIZ_COUNT * SECONDS_PER_QUESTION_CHALLENGE;
        }
        Object raw = sessionState.get(SESSION_STATE_TIME_LIMIT_SECONDS);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return INITIAL_CHALLENGE_QUIZ_COUNT * SECONDS_PER_QUESTION_CHALLENGE;
    }

    private long extractTimerStartedAtEpochSeconds(Map<String, Object> sessionState) {
        if (sessionState == null) {
            return 0L;
        }
        Object raw = sessionState.get(SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private Map<String, Object> sanitizeSessionStateForClient(Map<String, Object> sessionState) {
        if (sessionState == null || sessionState.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>(sessionState);
        sanitized.remove(SESSION_STATE_QUIZ);
        return sanitized;
    }

    private Map<String, Object> mergeSessionState(
            Map<String, Object> existingState,
            Map<String, Object> incomingState
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (existingState != null && !existingState.isEmpty()) {
            merged.putAll(existingState);
        }
        if (incomingState != null && !incomingState.isEmpty()) {
            Object selectedChoices = incomingState.get(SESSION_STATE_SELECTED_CHOICES);
            if (selectedChoices instanceof Map<?, ?>) {
                merged.put(SESSION_STATE_SELECTED_CHOICES, selectedChoices);
            }
        }
        if (!merged.containsKey(SESSION_STATE_SELECTED_CHOICES)) {
            merged.put(SESSION_STATE_SELECTED_CHOICES, Map.of());
        }
        if (!merged.containsKey(SESSION_STATE_TIME_LIMIT_SECONDS)) {
            merged.put(SESSION_STATE_TIME_LIMIT_SECONDS, INITIAL_CHALLENGE_QUIZ_COUNT * SECONDS_PER_QUESTION_CHALLENGE);
        }
        if (!merged.containsKey(SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS)) {
            merged.put(SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS, OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond());
        }
        if (!merged.containsKey(SESSION_STATE_COMPLETED)) {
            merged.put(SESSION_STATE_COMPLETED, false);
        }
        return merged;
    }

    private Map<String, Object> markSessionStateCompleted(Map<String, Object> existingState) {
        Map<String, Object> nextState = new LinkedHashMap<>();
        if (existingState != null && !existingState.isEmpty()) {
            nextState.putAll(existingState);
        }
        nextState.put(SESSION_STATE_COMPLETED, true);
        return nextState;
    }

    private List<String> extractWeakConcepts(QuickReviewSessionEntity session) {
        if (session.getSessionMetadata() == null) {
            return List.of();
        }
        Object weakConceptsRaw = session.getSessionMetadata().get(SESSION_METADATA_WEAK_CONCEPTS);
        if (!(weakConceptsRaw instanceof List<?> weakConceptsList) || weakConceptsList.isEmpty()) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>(weakConceptsList.size());
        for (Object value : weakConceptsList) {
            if (!(value instanceof String concept)) {
                continue;
            }
            String trimmed = concept.trim();
            if (!trimmed.isBlank()) {
                normalized.add(trimmed);
            }
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(normalized));
    }

    private List<ChallengeQuizConceptStatResponse> extractConceptBreakdown(QuickReviewSessionEntity session) {
        if (session.getSessionMetadata() == null) {
            return List.of();
        }
        Object conceptBreakdownRaw = session.getSessionMetadata().get(SESSION_METADATA_CONCEPT_BREAKDOWN);
        if (!(conceptBreakdownRaw instanceof List<?> conceptBreakdownList) || conceptBreakdownList.isEmpty()) {
            return List.of();
        }

        List<ChallengeQuizConceptStatResponse> conceptStats = new ArrayList<>(conceptBreakdownList.size());
        for (Object entry : conceptBreakdownList) {
            if (!(entry instanceof Map<?, ?> valueMap)) {
                continue;
            }

            String concept = readString(valueMap.get(CONCEPT_KEY));
            Integer correctAnswers = readInteger(valueMap.get(CORRECT_ANSWERS_KEY));
            Integer totalQuestions = readInteger(valueMap.get(TOTAL_QUESTIONS_KEY));
            Integer accuracyPercentage = readInteger(valueMap.get(ACCURACY_PERCENTAGE_KEY));
            if (concept == null || correctAnswers == null || totalQuestions == null || accuracyPercentage == null) {
                continue;
            }

            conceptStats.add(new ChallengeQuizConceptStatResponse(
                    concept,
                    correctAnswers,
                    totalQuestions,
                    accuracyPercentage
            ));
        }
        return conceptStats.isEmpty() ? List.of() : List.copyOf(conceptStats);
    }

    private String readString(Object value) {
        if (!(value instanceof String raw)) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private Integer readInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String raw) {
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private ChallengeStatistics computeStatistics(
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices,
            int fallbackCorrectAnswers,
            int fallbackTotalQuestions
    ) {
        if (quiz == null || quiz.isEmpty()) {
            int totalQuestions = Math.max(1, fallbackTotalQuestions);
            int correctAnswers = Math.clamp(fallbackCorrectAnswers, 0, totalQuestions);
            BigDecimal percentage = BigDecimal.valueOf(correctAnswers)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalQuestions), 2, RoundingMode.HALF_UP);
            return new ChallengeStatistics(
                    correctAnswers,
                    totalQuestions,
                    resolvePerformanceLevel(percentage),
                    List.of(),
                    List.of()
            );
        }

        Map<String, ConceptCounter> conceptCounters = new LinkedHashMap<>();
        int correctAnswers = 0;
        for (int index = 0; index < quiz.size(); index++) {
            QuizItem item = quiz.get(index);
            if (item == null) {
                continue;
            }
            String concept = normalizeConcept(item.concept());
            ConceptCounter counter = conceptCounters.computeIfAbsent(concept, unused -> new ConceptCounter());
            counter.totalQuestions += 1;

            Integer selectedChoiceIndex = selectedChoices.get(index);
            if (selectedChoiceIndex != null && selectedChoiceIndex.equals(item.correctIndex())) {
                counter.correctAnswers += 1;
                correctAnswers += 1;
            }
        }

        int totalQuestions = selectedChoices.isEmpty() ? quiz.size() : selectedChoices.size();
        BigDecimal percentage = BigDecimal.valueOf(correctAnswers)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(Math.max(1, totalQuestions)), 2, RoundingMode.HALF_UP);

        List<ChallengeQuizConceptStatResponse> conceptBreakdown = conceptCounters.entrySet()
                .stream()
                .map(entry -> {
                    int accuracy = calculateAccuracy(entry.getValue().correctAnswers, entry.getValue().totalQuestions);
                    return new ChallengeQuizConceptStatResponse(
                            entry.getKey(),
                            entry.getValue().correctAnswers,
                            entry.getValue().totalQuestions,
                            accuracy
                    );
                })
                .toList();

        List<String> weakConcepts = conceptBreakdown.stream()
                .filter(stat -> stat.accuracyPercentage() < WEAK_CONCEPT_ACCURACY_THRESHOLD)
                .map(ChallengeQuizConceptStatResponse::concept)
                .toList();

        return new ChallengeStatistics(
                correctAnswers,
                totalQuestions,
                resolvePerformanceLevel(percentage),
                conceptBreakdown,
                weakConcepts
        );
    }

    private String normalizeConcept(String value) {
        if (value == null) {
            return UNKNOWN_CONCEPT_LABEL;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? UNKNOWN_CONCEPT_LABEL : normalized;
    }

    private int calculateAccuracy(int correctAnswers, int totalQuestions) {
        if (totalQuestions <= 0) {
            return 0;
        }
        return (int) Math.round((correctAnswers * 100.0) / totalQuestions);
    }

    private String resolvePerformanceLevel(BigDecimal scorePercentage) {
        if (scorePercentage.compareTo(BigDecimal.valueOf(HIGH_SCORE_THRESHOLD)) >= 0) {
            return PERFORMANCE_LEVEL_EXCELLENT;
        }
        if (scorePercentage.compareTo(BigDecimal.valueOf(75)) >= 0) {
            return PERFORMANCE_LEVEL_GOOD;
        }
        if (scorePercentage.compareTo(BigDecimal.valueOf(LOW_SCORE_THRESHOLD)) >= 0) {
            return PERFORMANCE_LEVEL_FAIR;
        }
        return PERFORMANCE_LEVEL_NEEDS_IMPROVEMENT;
    }

    private Map<String, Object> buildCompletionSessionMetadata(ChallengeStatistics statistics) {
        List<Map<String, Object>> conceptBreakdown = statistics.conceptBreakdown().stream()
                .map(stat -> Map.<String, Object>of(
                        CONCEPT_KEY, stat.concept(),
                        CORRECT_ANSWERS_KEY, stat.correctAnswers(),
                        TOTAL_QUESTIONS_KEY, stat.totalQuestions(),
                        ACCURACY_PERCENTAGE_KEY, stat.accuracyPercentage()
                ))
                .toList();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(SESSION_METADATA_WEAK_CONCEPTS, statistics.weakConcepts());
        metadata.put(SESSION_METADATA_CONCEPT_BREAKDOWN, conceptBreakdown);
        return metadata;
    }

    private static final class ConceptCounter {
        private int correctAnswers;
        private int totalQuestions;
    }

    private record ChallengeStatistics(
            int correctAnswers,
            int totalQuestions,
            String performanceLevel,
            List<ChallengeQuizConceptStatResponse> conceptBreakdown,
            List<String> weakConcepts
    ) {
    }

    private record ChallengeGenerationProfile(int questionCount, String difficulty) {
    }

    private UUID parseStudyPackId(String studyPackIdRaw) {
        try {
            return UUID.fromString(Objects.requireNonNull(studyPackIdRaw).trim());
        } catch (RuntimeException ex) {
            throw new StudyPackNotFoundException();
        }
    }

    private UUID parseSessionId(String sessionIdRaw) {
        try {
            return UUID.fromString(Objects.requireNonNull(sessionIdRaw).trim());
        } catch (RuntimeException ex) {
            throw new ChallengeQuizSessionNotFoundException();
        }
    }

    private StudyPackEntity findOwnedStudyPackOrThrow(UUID studyPackId, UUID userId) {
        return studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(StudyPackNotFoundException::new);
    }

    private StudyPackGenerationContext buildQuizGenerationContext(UUID userId, StudyPackEntity studyPack) {
        return generationContextResolver.resolveForStudyPack(userId, studyPack);
    }

    private QuickReviewSessionEntity findChallengeSessionOrThrow(UUID sessionId, UUID userId) {
        return quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.CHALLENGE
                )
                .orElseThrow(ChallengeQuizSessionNotFoundException::new);
    }

    private QuickReviewSessionEntity findChallengeSessionForUpdateOrThrow(UUID sessionId, UUID userId) {
        return quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.CHALLENGE
                )
                .orElseThrow(ChallengeQuizSessionNotFoundException::new);
    }

    private void assertSessionInProgress(QuickReviewSessionEntity session) {
        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            throw new ChallengeQuizSessionNotInProgressException();
        }
    }

    private void markSessionForfeited(QuickReviewSessionEntity session) {
        session.setStatus(QuickReviewSessionStatus.FORFEITED);
        session.setCompletedAt(null);
    }

    private List<String> getKeyConcepts(StudyPackEntity studyPack) {
        return studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts();
    }

    private int resolveMonthlyChallengeQuizLimit(PlanType planType) {
        return properties.getPricing().resolveMonthlyChallengeQuizLimit(planType);
    }

    private boolean isDifficultySelectionAvailable(PlanType planType) {
        return properties.getPricing().isDifficultySelectionAvailable(planType);
    }

    private ChallengeQuizStartResponse buildEmptyStartResponse(
            StudyPackEntity studyPack,
            int usedThisMonth,
            PlanType planType
    ) {
        return new ChallengeQuizStartResponse(
                null,
                null,
                studyPack.getId().toString(),
                studyPack.getTitle(),
                0,
                INITIAL_CHALLENGE_QUIZ_COUNT * SECONDS_PER_QUESTION_CHALLENGE,
                usedThisMonth,
                resolveMonthlyChallengeQuizLimit(planType),
                isDifficultySelectionAvailable(planType),
                MODE_CHALLENGE,
                DEFAULT_SELECTED_DIFFICULTY,
                List.of(),
                0,
                null
        );
    }

    private StudyPackEntity findOwnedStudyPackForGenerationOrThrow(UUID studyPackId, UUID userId) {
        return studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)
                .orElseThrow(StudyPackNotFoundException::new);
    }

    private QuickReviewSessionEntity buildGeneratingSession(
            UUID userId,
            UUID studyPackId,
            StudyPackEntity studyPack,
            String difficulty,
            String mode
    ) {
        OffsetDateTime createdAt = OffsetDateTime.now();
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(studyPack.getNoteId());
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.GENERATING);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(0);
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        session.setRetryCount(0);
        session.setSessionMetadata(null);
        session.setSessionState(buildInitialSessionState(difficulty, mode));
        session.setCreatedAt(createdAt);
        session.setCompletedAt(null);
        return session;
    }

    private void markSessionReady(QuickReviewSessionEntity session, List<QuizItem> challengeQuiz, String difficulty) {
        String mode = extractMode(session.getSessionState());
        int rateSeconds = MODE_BOARD_EXAM.equals(mode) ? SECONDS_PER_QUESTION_BOARD_EXAM : SECONDS_PER_QUESTION_CHALLENGE;
        int timeLimitSeconds = challengeQuiz.size() * rateSeconds;
        Map<String, Object> state = buildInitialSessionState(difficulty, mode);
        state.put(SESSION_STATE_TIME_LIMIT_SECONDS, timeLimitSeconds);
        state.put(SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS, OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond());

        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(challengeQuiz.size());
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        session.setRetryCount(0);
        session.setSessionMetadata(null);
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                challengeQuiz,
                state
        ));
        session.setCompletedAt(null);
    }

    private void markSessionFailed(QuickReviewSessionEntity session) {
        session.setStatus(QuickReviewSessionStatus.FAILED);
        session.setCurrentQuestionIndex(0);
        session.setTotalQuestions(0);
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        session.setCompletedAt(null);
    }

}
