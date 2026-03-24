package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ChallengeQuizCompleteRequest;
import com.studysnap.backend.dto.ChallengeQuizConceptStatResponse;
import com.studysnap.backend.dto.ChallengeQuizPerformanceSummaryResponse;
import com.studysnap.backend.dto.ChallengeQuizProgressRequest;
import com.studysnap.backend.dto.ChallengeQuizSessionResponse;
import com.studysnap.backend.dto.ChallengeQuizSessionSummaryResponse;
import com.studysnap.backend.dto.ChallengeQuizStartResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.util.QuizDeduplicationUtils;
import com.studysnap.backend.util.QuizSessionStateUtils;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
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
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ChallengeQuizService {
    private static final String SESSION_STATE_TIME_LIMIT_SECONDS = "timeLimitSeconds";
    private static final String SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS = "timerStartedAtEpochSeconds";
    private static final String SESSION_STATE_SELECTED_CHOICES = "selectedChoices";
    private static final String SESSION_STATE_COMPLETED = "completed";
    private static final String SESSION_METADATA_WEAK_CONCEPTS = "weakConcepts";
    private static final String SESSION_METADATA_CONCEPT_BREAKDOWN = "conceptBreakdown";
    private static final String UNKNOWN_CONCEPT_LABEL = "Uncategorized";
    private static final int LOW_SCORE_QUESTION_COUNT = 10;
    private static final int MID_SCORE_QUESTION_COUNT = 12;
    private static final int HIGH_SCORE_QUESTION_COUNT = 15;
    private static final int DEFAULT_TIME_LIMIT_SECONDS = 600;

    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final LlmStudyPackService llmStudyPackService;
    private final FeatureGateService featureGateService;
    private final StudySnapProperties properties;
    private final UserUsageService userUsageService;
    private final BillingUsagePeriodService billingUsagePeriodService;
    private final AuthService authService;
    private final AnalyticsService analyticsService;
    private final AiRateLimitService aiRateLimitService;

    public ChallengeQuizStartResponse startSession(String studyPackIdRaw, UUID userId) {
        authService.requireEmailVerified(userId);
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                studyPackIdRaw,
                "STUDY_PACK_NOT_FOUND",
                "Study pack not found.",
                HttpStatus.NOT_FOUND
        );
        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));
        featureGateService.checkFeatureAccess(userId, Feature.CHALLENGE_QUIZ);

        QuickReviewSessionEntity existing = quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.CHALLENGE,
                        QuickReviewSessionStatus.IN_PROGRESS
                )
                .orElse(null);
        if (existing != null) {
            List<QuizItem> existingQuiz = QuizSessionStateUtils.extractQuiz(existing.getSessionState());
            if (!existingQuiz.isEmpty()) {
                int usedThisMonth = (int) countChallengeQuizUsedThisMonth(userId);
                return toStartResponse(existing, studyPack, usedThisMonth);
            }
            existing.setStatus(QuickReviewSessionStatus.COMPLETED);
            existing.setCompletedAt(OffsetDateTime.now());
            quickReviewSessionRepository.save(existing);
        }

        int usedThisMonth = assertChallengeQuizQuotaAvailable(userId);
        aiRateLimitService.assertAllowed(userId, PlanType.PREMIUM, "challenge-quiz");
        ChallengeGenerationProfile profile = resolveGenerationProfile(userId, studyPackId);
        List<String> disallowedQuestions = extractQuestionTexts(studyPack.getQuiz());
        List<QuizItem> generatedQuiz = llmStudyPackService.generateChallengeQuiz(
                studyPack.getTitle(),
                studyPack.getSummary(),
                studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts(),
                disallowedQuestions,
                profile.questionCount(),
                profile.difficulty()
        );
        List<QuizItem> challengeQuiz = QuizDeduplicationUtils.uniqueQuestions(
                generatedQuiz,
                QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(disallowedQuestions)
        );
        if (challengeQuiz.size() != profile.questionCount()) {
            throw new AppException(
                    "CHALLENGE_QUIZ_GENERATION_FAILED",
                    "Could not generate enough unique challenge questions. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        }

        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(studyPack.getNoteId());
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
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
                buildInitialSessionState(profile.difficulty())
        ));
        session.setCreatedAt(OffsetDateTime.now());
        session.setCompletedAt(null);

        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        userUsageService.incrementChallengeQuizGeneration(userId, saved.getCreatedAt());
        analyticsService.trackEvent(userId, AnalyticsEventType.CHALLENGE_QUIZ_STARTED, studyPackId, Map.of(
                "sessionId", saved.getId().toString(),
                "questionCount", challengeQuiz.size()
        ));
        return toStartResponse(saved, studyPack, usedThisMonth + 1);
    }

    @Transactional(readOnly = true)
    public ChallengeQuizStartResponse getInProgressSession(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                studyPackIdRaw,
                "STUDY_PACK_NOT_FOUND",
                "Study pack not found.",
                HttpStatus.NOT_FOUND
        );
        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));

        int usedThisMonth = (int) countChallengeQuizUsedThisMonth(userId);
        return quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.CHALLENGE,
                        QuickReviewSessionStatus.IN_PROGRESS
                )
                .map(session -> toStartResponse(session, studyPack, usedThisMonth))
                .orElse(new ChallengeQuizStartResponse(
                        null,
                        studyPack.getId().toString(),
                        studyPack.getTitle(),
                        0,
                        DEFAULT_TIME_LIMIT_SECONDS,
                        usedThisMonth,
                        properties.getPricing().getPremiumMonthlyChallengeQuizLimit(),
                        List.of(),
                        0,
                        null
                ));
    }

    @Transactional(readOnly = true)
    public List<ChallengeQuizSessionSummaryResponse> listRecentSessions(String studyPackIdRaw, UUID userId, int limit) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                studyPackIdRaw,
                "STUDY_PACK_NOT_FOUND",
                "Study pack not found.",
                HttpStatus.NOT_FOUND
        );
        studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));

        int normalizedLimit = Math.max(1, Math.min(limit, 10));
        return quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.CHALLENGE,
                        PageRequest.of(0, normalizedLimit)
                ).stream()
                .map(this::toSessionSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChallengeQuizPerformanceSummaryResponse getPerformanceSummary(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                studyPackIdRaw,
                "STUDY_PACK_NOT_FOUND",
                "Study pack not found.",
                HttpStatus.NOT_FOUND
        );
        studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));

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
                        PageRequest.of(0, 1)
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
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(
                sessionIdRaw,
                "SESSION_NOT_FOUND",
                "Challenge Quiz session not found.",
                HttpStatus.NOT_FOUND
        );
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.CHALLENGE
                )
                .orElseThrow(() -> new AppException("SESSION_NOT_FOUND", "Challenge Quiz session not found.", HttpStatus.NOT_FOUND));

        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            throw new AppException(
                    "SESSION_NOT_IN_PROGRESS",
                    "Challenge Quiz session is already completed.",
                    HttpStatus.BAD_REQUEST
            );
        }

        int totalQuestions = session.getTotalQuestions() == null ? 0 : session.getTotalQuestions();
        int normalizedIndex = Math.max(0, Math.min(request.currentQuestionIndex(), Math.max(0, totalQuestions - 1)));
        session.setCurrentQuestionIndex(normalizedIndex);
        session.setSessionState(mergeSessionState(session.getSessionState(), request.sessionState()));
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);

        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserId(saved.getStudyPackId(), userId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));
        int usedThisMonth = (int) countChallengeQuizUsedThisMonth(userId);
        return toStartResponse(saved, studyPack, usedThisMonth);
    }

    public ChallengeQuizSessionResponse completeSession(String sessionIdRaw, UUID userId, ChallengeQuizCompleteRequest request) {
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(
                sessionIdRaw,
                "SESSION_NOT_FOUND",
                "Challenge Quiz session not found.",
                HttpStatus.NOT_FOUND
        );
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.CHALLENGE
                )
                .orElseThrow(() -> new AppException("SESSION_NOT_FOUND", "Challenge Quiz session not found.", HttpStatus.NOT_FOUND));

        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            throw new AppException(
                    "SESSION_NOT_IN_PROGRESS",
                    "Challenge Quiz session is already completed.",
                    HttpStatus.BAD_REQUEST
            );
        }
        int totalQuestions = session.getTotalQuestions() == null ? request.totalQuestions() : session.getTotalQuestions();
        if (request.correctAnswers() > totalQuestions) {
            throw new AppException(
                    "INVALID_SESSION_RESULT",
                    "Correct answers cannot exceed total questions.",
                    HttpStatus.BAD_REQUEST
            );
        }

        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        Map<Integer, String> selectedChoices = extractSelectedChoices(session.getSessionState());
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

    private int assertChallengeQuizQuotaAvailable(UUID userId) {
        long usedThisMonth = countChallengeQuizUsedThisMonth(userId);
        int monthlyLimit = properties.getPricing().getPremiumMonthlyChallengeQuizLimit();
        if (usedThisMonth < monthlyLimit) {
            return (int) usedThisMonth;
        }

        throw new AppException(
                "MONTHLY_CHALLENGE_QUIZ_LIMIT_REACHED",
                "You've reached your monthly Challenge Quiz limit.",
                HttpStatus.FORBIDDEN
        );
    }

    private long countChallengeQuizUsedThisMonth(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        BillingUsagePeriodService.UsagePeriod usagePeriod = billingUsagePeriodService.resolveUsagePeriod(userId, now);
        long usedFromSessions = quickReviewSessionRepository.countByUserIdAndSessionModeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                QuickReviewSessionMode.CHALLENGE,
                usagePeriod.periodStart(),
                usagePeriod.periodEnd()
        );
        long usedFromUsage = userUsageService.getMonthlyUsage(userId, now).challengeQuizGenerations();
        return Math.max(usedFromSessions, usedFromUsage);
    }

    private ChallengeGenerationProfile resolveGenerationProfile(UUID userId, UUID studyPackId) {
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
            return new ChallengeGenerationProfile(MID_SCORE_QUESTION_COUNT, "medium");
        }

        if (previousScore.compareTo(BigDecimal.valueOf(50)) < 0) {
            return new ChallengeGenerationProfile(LOW_SCORE_QUESTION_COUNT, "easy-medium");
        }
        if (previousScore.compareTo(BigDecimal.valueOf(80)) < 0) {
            return new ChallengeGenerationProfile(MID_SCORE_QUESTION_COUNT, "medium");
        }
        return new ChallengeGenerationProfile(HIGH_SCORE_QUESTION_COUNT, "medium-hard");
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

    private ChallengeQuizStartResponse toStartResponse(QuickReviewSessionEntity session, StudyPackEntity studyPack, int usedThisMonth) {
        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        if (quiz.isEmpty()) {
            throw new AppException(
                    "CHALLENGE_QUIZ_NOT_AVAILABLE",
                    "Challenge Quiz session is not available. Please start again.",
                    HttpStatus.BAD_REQUEST
            );
        }
        int timeLimitSeconds = extractTimeLimitSeconds(session.getSessionState());
        int limit = properties.getPricing().getPremiumMonthlyChallengeQuizLimit();
        return new ChallengeQuizStartResponse(
                session.getId().toString(),
                studyPack.getId().toString(),
                studyPack.getTitle(),
                quiz.size(),
                timeLimitSeconds,
                usedThisMonth,
                limit,
                quiz,
                session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex(),
                sanitizeSessionStateForClient(session.getSessionState())
        );
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

    private Map<String, Object> buildInitialSessionState(String difficulty) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(SESSION_STATE_TIME_LIMIT_SECONDS, DEFAULT_TIME_LIMIT_SECONDS);
        state.put(SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS, OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond());
        state.put(SESSION_STATE_SELECTED_CHOICES, Map.of());
        state.put(SESSION_STATE_COMPLETED, false);
        state.put("difficulty", difficulty);
        return state;
    }

    private int extractTimeLimitSeconds(Map<String, Object> sessionState) {
        if (sessionState == null) {
            return DEFAULT_TIME_LIMIT_SECONDS;
        }
        Object raw = sessionState.get(SESSION_STATE_TIME_LIMIT_SECONDS);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return DEFAULT_TIME_LIMIT_SECONDS;
    }

    private Map<String, Object> sanitizeSessionStateForClient(Map<String, Object> sessionState) {
        if (sessionState == null || sessionState.isEmpty()) {
            return null;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>(sessionState);
        sanitized.remove("quiz");
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
            merged.put(SESSION_STATE_TIME_LIMIT_SECONDS, DEFAULT_TIME_LIMIT_SECONDS);
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

    private Map<Integer, String> extractSelectedChoices(Map<String, Object> sessionState) {
        if (sessionState == null || sessionState.isEmpty()) {
            return Map.of();
        }
        Object raw = sessionState.get(SESSION_STATE_SELECTED_CHOICES);
        if (!(raw instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return Map.of();
        }

        Map<Integer, String> selectedChoices = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (!(key instanceof String keyString) || !(value instanceof String selectedChoice)) {
                continue;
            }
            try {
                int questionIndex = Integer.parseInt(keyString);
                if (questionIndex >= 0) {
                    selectedChoices.put(questionIndex, selectedChoice);
                }
            } catch (NumberFormatException ignored) {
                // Ignore invalid question index keys.
            }
        }
        return selectedChoices;
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

            String concept = readString(valueMap.get("concept"));
            Integer correctAnswers = readInteger(valueMap.get("correctAnswers"));
            Integer totalQuestions = readInteger(valueMap.get("totalQuestions"));
            Integer accuracyPercentage = readInteger(valueMap.get("accuracyPercentage"));
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
            Map<Integer, String> selectedChoices,
            int fallbackCorrectAnswers,
            int fallbackTotalQuestions
    ) {
        if (quiz == null || quiz.isEmpty()) {
            int totalQuestions = Math.max(1, fallbackTotalQuestions);
            int correctAnswers = Math.max(0, Math.min(fallbackCorrectAnswers, totalQuestions));
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

            String selectedChoice = selectedChoices.get(index);
            if (selectedChoice != null && selectedChoice.equals(item.answer())) {
                counter.correctAnswers += 1;
                correctAnswers += 1;
            }
        }

        int totalQuestions = quiz.size();
        BigDecimal percentage = BigDecimal.valueOf(correctAnswers)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalQuestions), 2, RoundingMode.HALF_UP);

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
                .filter(stat -> stat.accuracyPercentage() < 60)
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
        if (scorePercentage.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return "Excellent";
        }
        if (scorePercentage.compareTo(BigDecimal.valueOf(75)) >= 0) {
            return "Good";
        }
        if (scorePercentage.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return "Fair";
        }
        return "Needs Improvement";
    }

    private Map<String, Object> buildCompletionSessionMetadata(ChallengeStatistics statistics) {
        List<Map<String, Object>> conceptBreakdown = statistics.conceptBreakdown().stream()
                .map(stat -> Map.<String, Object>of(
                        "concept", stat.concept(),
                        "correctAnswers", stat.correctAnswers(),
                        "totalQuestions", stat.totalQuestions(),
                        "accuracyPercentage", stat.accuracyPercentage()
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
}
