package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.QuickReviewAdaptiveQuizResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.AdaptivePracticeSessionNotFoundException;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.StudyPackNotFoundException;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizDeduplicationUtils;
import com.studysnap.backend.util.QuizSessionStateUtils;
import com.studysnap.backend.util.UuidParsingUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class QuickReviewAdaptivePracticeService {

    private static final int BASE_QUESTION_COUNT = 5;
    private static final int MID_QUESTION_COUNT = 7;
    private static final int HIGH_QUESTION_COUNT = 10;
    private static final String FOCUS_MESSAGE = "Focusing on concepts you need to improve.";
    private static final String ADAPTIVE_GENERATING_MESSAGE = "Generating your adaptive practice.";
    private static final String ADAPTIVE_GENERATION_FAILED_MESSAGE = "We couldn't generate Adaptive Practice this time. Try again.";
    private static final String NO_SOURCE_SESSION_MESSAGE = "Complete a Quick Review or Challenge Quiz first to unlock adaptive practice.";
    private static final String NO_WEAK_CONCEPTS_MESSAGE = "No weak concepts found from your latest review.";
    private static final String ADAPTIVE_GENERATION_FAILED_CODE = "ADAPTIVE_QUIZ_GENERATION_FAILED";
    private static final String ADAPTIVE_GENERATION_FAILED_DETAIL = "Could not generate enough unique adaptive questions. Please try again.";
    private static final String PREMIUM_FEATURE_REQUIRED_CODE = "PREMIUM_FEATURE_REQUIRED";
    private static final String PREMIUM_FEATURE_REQUIRED_MESSAGE = "Adaptive Practice is a Premium feature. Upgrade to Premium to continue.";
    private static final String MONTHLY_LIMIT_REACHED_CODE = "MONTHLY_ADAPTIVE_PRACTICE_LIMIT_REACHED";
    private static final String MONTHLY_LIMIT_REACHED_MESSAGE = "You've reached your monthly Adaptive Practice limit.";
    private static final String INVALID_SESSION_RESULT_CODE = "INVALID_SESSION_RESULT";
    private static final String INVALID_SESSION_RESULT_MESSAGE = "Correct answers cannot exceed total questions.";
    private static final String ADAPTIVE_PRACTICE_SESSION_COMPLETED_MESSAGE = "Adaptive Practice session completed.";
    private static final String ADAPTIVE_PRACTICE_SESSION_ALREADY_COMPLETED_MESSAGE = "Adaptive Practice session already completed.";
    private static final String ADAPTIVE_PRACTICE_SESSION_ALREADY_ENDED_MESSAGE = "Adaptive Practice session has already ended.";
    private static final String ADAPTIVE_PRACTICE_SESSION_FORFEITED_MESSAGE = "Adaptive Practice session forfeited.";
    private static final String AI_RATE_LIMIT_SCOPE = "adaptive-practice";
    private static final String SESSION_METADATA_WEAK_CONCEPTS = "weakConcepts";
    private static final String ANALYTICS_METADATA_SESSION_ID = "sessionId";
    private static final String ANALYTICS_METADATA_WEAK_CONCEPT_COUNT = "weakConceptCount";
    private static final List<QuickReviewSessionStatus> ACTIVE_GENERATION_STATUSES = List.of(
        QuickReviewSessionStatus.GENERATING,
        QuickReviewSessionStatus.IN_PROGRESS
    );
    private static final List<QuickReviewSessionStatus> OBSERVABLE_STATUSES = List.of(
        QuickReviewSessionStatus.GENERATING,
        QuickReviewSessionStatus.IN_PROGRESS,
        QuickReviewSessionStatus.FAILED
    );

    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final ActivityEventRepository activityEventRepository;
    private final QuizGenerationService quizGenerationService;
    private final ActivityTrackingService activityTrackingService;
    private final SubscriptionService subscriptionService;
    private final FeatureGateService featureGateService;
    private final StudySnapProperties properties;
    private final UserUsageService userUsageService;
    private final BillingUsagePeriodService billingUsagePeriodService;
    private final AuthService authService;
    private final AnalyticsService analyticsService;
    private final AiRateLimitService aiRateLimitService;

    public QuickReviewAdaptiveQuizResponse generateAdaptiveQuiz(String studyPackIdRaw, UUID userId) {
        authService.requireEmailVerified(userId);
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        StudyPackEntity studyPack = findOwnedStudyPackForGenerationOrThrow(studyPackId, userId);
        PlanType planType = subscriptionService.resolvePlan(userId);
        featureGateService.checkFeatureAccess(planType, Feature.ADAPTIVE_QUIZ);

        QuickReviewSessionEntity existing = quickReviewSessionRepository
            .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                userId,
                studyPackId,
                QuickReviewSessionMode.ADAPTIVE,
                ACTIVE_GENERATION_STATUSES
            )
            .orElse(null);
        if (existing != null) {
            List<QuizItem> existingQuiz = QuizSessionStateUtils.extractQuiz(existing.getSessionState());
            if (existing.getStatus() == QuickReviewSessionStatus.GENERATING || !existingQuiz.isEmpty()) {
                return toAdaptiveResponse(existing, studyPack);
            }
            markSessionForfeited(existing);
            quickReviewSessionRepository.save(existing);
        }

        QuickReviewSessionEntity latestCompletedSession = resolveLatestAdaptiveSourceSession(userId, studyPackId);

        if (latestCompletedSession == null) {
            return new QuickReviewAdaptiveQuizResponse(
                null,
                null,
                studyPack.getId().toString(),
                studyPack.getTitle(),
                List.of(),
                List.of(),
                NO_SOURCE_SESSION_MESSAGE
            );
        }

        List<String> weakConcepts = extractWeakConcepts(latestCompletedSession);
        if (weakConcepts.isEmpty()) {
            return new QuickReviewAdaptiveQuizResponse(
                null,
                null,
                studyPack.getId().toString(),
                studyPack.getTitle(),
                List.of(),
                List.of(),
                NO_WEAK_CONCEPTS_MESSAGE
            );
        }

        assertAdaptivePracticeQuotaAvailable(userId, planType);
        aiRateLimitService.assertAllowed(userId, planType, AI_RATE_LIMIT_SCOPE);
        int questionCount = resolveAdaptiveQuestionCount(weakConcepts.size());
        QuickReviewSessionEntity session = quickReviewSessionRepository.save(buildGeneratingSession(
            userId,
            studyPackId,
            studyPack,
            weakConcepts
        ));
        List<String> disallowedQuestions = extractQuestionTexts(studyPack.getQuiz());
        StudyPackGenerationContext generationContext = buildQuizGenerationContext(userId, studyPack);
        try {
            List<QuizItem> generatedQuiz = quizGenerationService.generateAdaptivePracticeQuiz(
                studyPack.getTitle(),
                studyPack.getSummary(),
                studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts(),
                weakConcepts,
                disallowedQuestions,
                questionCount,
                generationContext
            );
            List<QuizItem> adaptiveQuiz = QuizDeduplicationUtils.uniqueQuestions(
                generatedQuiz,
                QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(disallowedQuestions)
            );
            if (adaptiveQuiz.size() != questionCount) {
                throw new AppException(
                    ADAPTIVE_GENERATION_FAILED_CODE,
                    ADAPTIVE_GENERATION_FAILED_DETAIL,
                    HttpStatus.BAD_GATEWAY
                );
            }

            markSessionReady(session, adaptiveQuiz, weakConcepts);
            QuickReviewSessionEntity savedSession = quickReviewSessionRepository.save(session);
            userUsageService.incrementAdaptiveQuizGeneration(userId, savedSession.getCreatedAt());

            try {
                activityTrackingService.recordActivity(userId, ActivityType.STARTED_ADAPTIVE_PRACTICE, studyPackId);
                analyticsService.trackEvent(userId, AnalyticsEventType.ADAPTIVE_PRACTICE_STARTED, studyPackId, Map.of(
                    ANALYTICS_METADATA_SESSION_ID, savedSession.getId().toString(),
                    ANALYTICS_METADATA_WEAK_CONCEPT_COUNT, weakConcepts.size()
                ));
            } catch (RuntimeException ignored) {
                // Activity/analytics failures must not turn a generated quiz into a failed session.
            }

            return toAdaptiveResponse(savedSession, studyPack);
        } catch (Exception ex) {
            markSessionFailed(session);
            QuickReviewSessionEntity failedSession = quickReviewSessionRepository.save(session);
            return toAdaptiveResponse(failedSession, studyPack);
        }
    }

    @Transactional(readOnly = true)
    public QuickReviewAdaptiveQuizResponse getAdaptiveQuizSession(String studyPackIdRaw, UUID userId) {
        authService.requireEmailVerified(userId);
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        StudyPackEntity studyPack = findOwnedStudyPackOrThrow(studyPackId, userId);
        PlanType planType = subscriptionService.resolvePlan(userId);
        featureGateService.checkFeatureAccess(planType, Feature.ADAPTIVE_QUIZ);

        QuickReviewSessionEntity existing = quickReviewSessionRepository
            .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                userId,
                studyPackId,
                QuickReviewSessionMode.ADAPTIVE,
                OBSERVABLE_STATUSES
            )
            .orElse(null);
        if (existing != null) {
            return toAdaptiveResponse(existing, studyPack);
        }

        QuickReviewSessionEntity latestCompletedSession = resolveLatestAdaptiveSourceSession(userId, studyPackId);
        if (latestCompletedSession == null) {
            return new QuickReviewAdaptiveQuizResponse(
                null,
                null,
                studyPack.getId().toString(),
                studyPack.getTitle(),
                List.of(),
                List.of(),
                NO_SOURCE_SESSION_MESSAGE
            );
        }

        List<String> weakConcepts = extractWeakConcepts(latestCompletedSession);
        if (weakConcepts.isEmpty()) {
            return new QuickReviewAdaptiveQuizResponse(
                null,
                null,
                studyPack.getId().toString(),
                studyPack.getTitle(),
                List.of(),
                List.of(),
                NO_WEAK_CONCEPTS_MESSAGE
            );
        }

        return new QuickReviewAdaptiveQuizResponse(
            null,
            null,
            studyPack.getId().toString(),
            studyPack.getTitle(),
            weakConcepts,
            List.of(),
            FOCUS_MESSAGE
        );
    }

    public SimpleMessageResponse completeAdaptiveSession(
        String sessionIdRaw,
        UUID userId,
        Integer correctAnswers,
        Integer totalQuestions,
        Integer durationSeconds
    ) {
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(sessionIdRaw, AdaptivePracticeSessionNotFoundException::new);
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                sessionId,
                userId,
                QuickReviewSessionMode.ADAPTIVE
            )
            .orElseThrow(AdaptivePracticeSessionNotFoundException::new);

        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            return new SimpleMessageResponse(ADAPTIVE_PRACTICE_SESSION_ALREADY_COMPLETED_MESSAGE);
        }

        int safeTotalQuestions = session.getTotalQuestions() == null
            ? Optional.ofNullable(totalQuestions)
              .orElse(0)
            : session.getTotalQuestions();
        int safeCorrectAnswers = correctAnswers == null ? 0 : Math.max(0, correctAnswers);
        if (safeCorrectAnswers > safeTotalQuestions) {
            throw new AppException(
                INVALID_SESSION_RESULT_CODE,
                INVALID_SESSION_RESULT_MESSAGE,
                HttpStatus.BAD_REQUEST
            );
        }

        BigDecimal scorePercentage = safeTotalQuestions == 0
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(safeCorrectAnswers)
              .multiply(BigDecimal.valueOf(100))
              .divide(BigDecimal.valueOf(safeTotalQuestions), 2, RoundingMode.HALF_UP);
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(safeTotalQuestions);
        session.setTotalQuestions(safeTotalQuestions);
        session.setCorrectAnswers(safeCorrectAnswers);
        session.setScorePercentage(scorePercentage);
        session.setDurationSeconds(durationSeconds);
        session.setCompletedAt(OffsetDateTime.now());
        quickReviewSessionRepository.save(session);
        return new SimpleMessageResponse(ADAPTIVE_PRACTICE_SESSION_COMPLETED_MESSAGE);
    }

    public SimpleMessageResponse forfeitAdaptiveSession(String sessionIdRaw, UUID userId) {
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(sessionIdRaw, AdaptivePracticeSessionNotFoundException::new);
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                sessionId,
                userId,
                QuickReviewSessionMode.ADAPTIVE
            )
            .orElseThrow(AdaptivePracticeSessionNotFoundException::new);

        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            return new SimpleMessageResponse(ADAPTIVE_PRACTICE_SESSION_ALREADY_ENDED_MESSAGE);
        }

        markSessionForfeited(session);
        quickReviewSessionRepository.save(session);
        return new SimpleMessageResponse(ADAPTIVE_PRACTICE_SESSION_FORFEITED_MESSAGE);
    }

    private void markSessionForfeited(QuickReviewSessionEntity session) {
        session.setStatus(QuickReviewSessionStatus.FORFEITED);
        session.setCompletedAt(null);
    }

    private StudyPackEntity findOwnedStudyPackOrThrow(UUID studyPackId, UUID userId) {
        return studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
            .orElseThrow(StudyPackNotFoundException::new);
    }

    private StudyPackEntity findOwnedStudyPackForGenerationOrThrow(UUID studyPackId, UUID userId) {
        return studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)
            .orElseThrow(StudyPackNotFoundException::new);
    }

    private QuickReviewSessionEntity buildGeneratingSession(
        UUID userId,
        UUID studyPackId,
        StudyPackEntity studyPack,
        List<String> weakConcepts
    ) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(studyPack.getNoteId());
        session.setSessionMode(QuickReviewSessionMode.ADAPTIVE);
        session.setStatus(QuickReviewSessionStatus.GENERATING);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(0);
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        session.setRetryCount(0);
        session.setDurationSeconds(null);
        session.setSessionMetadata(Map.of(SESSION_METADATA_WEAK_CONCEPTS, weakConcepts));
        session.setSessionState(null);
        session.setCreatedAt(OffsetDateTime.now());
        session.setCompletedAt(null);
        return session;
    }

    private void markSessionReady(
        QuickReviewSessionEntity session,
        List<QuizItem> adaptiveQuiz,
        List<String> weakConcepts
    ) {
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(adaptiveQuiz.size());
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        session.setRetryCount(0);
        session.setDurationSeconds(null);
        session.setSessionMetadata(Map.of(SESSION_METADATA_WEAK_CONCEPTS, weakConcepts));
        session.setSessionState(QuizSessionStateUtils.withQuiz(adaptiveQuiz, null));
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

    private QuickReviewAdaptiveQuizResponse toAdaptiveResponse(
        QuickReviewSessionEntity session,
        StudyPackEntity studyPack
    ) {
        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        String message = switch (session.getStatus()) {
            case GENERATING -> ADAPTIVE_GENERATING_MESSAGE;
            case FAILED -> ADAPTIVE_GENERATION_FAILED_MESSAGE;
            default -> FOCUS_MESSAGE;
        };
        return new QuickReviewAdaptiveQuizResponse(
            session.getId().toString(),
            session.getStatus(),
            studyPack.getId().toString(),
            studyPack.getTitle(),
            extractWeakConcepts(session),
            quiz,
            message
        );
    }

    private List<String> extractWeakConcepts(QuickReviewSessionEntity session) {
        if (session.getSessionMetadata() == null) {
            return List.of();
        }
        Object weakConceptsRaw = session.getSessionMetadata().get(SESSION_METADATA_WEAK_CONCEPTS);
        if (!(weakConceptsRaw instanceof List<?> rawList)) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (Object raw : rawList) {
            if (!(raw instanceof String value)) {
                continue;
            }
            String concept = value.trim();
            if (!concept.isBlank()) {
                normalized.add(concept);
            }
        }
        return new ArrayList<>(normalized);
    }

    private int resolveAdaptiveQuestionCount(int weakConceptCount) {
        if (weakConceptCount <= 2) {
            return BASE_QUESTION_COUNT;
        }
        if (weakConceptCount <= 4) {
            return MID_QUESTION_COUNT;
        }
        return HIGH_QUESTION_COUNT;
    }

    private StudyPackGenerationContext buildQuizGenerationContext(UUID userId, StudyPackEntity studyPack) {
        MeResponse me = authService.getMe(userId);
        return new StudyPackGenerationContext(
            me.learnerLevel(),
            me.courseProgram(),
            studyPack.getSubject(),
            studyPack.getTags() == null ? List.of() : List.of(studyPack.getTags())
        );
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

    private void assertAdaptivePracticeQuotaAvailable(UUID userId, PlanType planType) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        BillingUsagePeriodService.UsagePeriod usagePeriod = billingUsagePeriodService.resolveUsagePeriod(userId, now);
        int monthlyLimit = properties.getPricing().resolveMonthlyAdaptivePracticeLimit(planType);
        if (monthlyLimit <= 0) {
            throw new AppException(
                PREMIUM_FEATURE_REQUIRED_CODE,
                PREMIUM_FEATURE_REQUIRED_MESSAGE,
                HttpStatus.FORBIDDEN
            );
        }

        long usedFromActivityEvents = activityEventRepository.countByUserIdAndActivityTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            userId,
            ActivityType.STARTED_ADAPTIVE_PRACTICE,
            usagePeriod.periodStart(),
            usagePeriod.periodEnd()
        );
        long usedFromUsage = userUsageService.getMonthlyUsage(userId, now).adaptiveQuizGenerations();
        long usedThisMonth = Math.max(usedFromActivityEvents, usedFromUsage);
        if (usedThisMonth < monthlyLimit) {
            return;
        }

        throw new AppException(
            MONTHLY_LIMIT_REACHED_CODE,
            MONTHLY_LIMIT_REACHED_MESSAGE,
            HttpStatus.FORBIDDEN
        );
    }

    private QuickReviewSessionEntity resolveLatestAdaptiveSourceSession(UUID userId, UUID studyPackId) {
        QuickReviewSessionEntity latestQuickReview = fetchLatestCompletedSession(
            userId,
            studyPackId,
            QuickReviewSessionMode.QUICK_REVIEW
        );
        QuickReviewSessionEntity latestChallenge = fetchLatestCompletedSession(
            userId,
            studyPackId,
            QuickReviewSessionMode.CHALLENGE
        );

        if (latestQuickReview == null) {
            return latestChallenge;
        }
        if (latestChallenge == null) {
            return latestQuickReview;
        }

        OffsetDateTime quickReviewCompletedAt = latestQuickReview.getCompletedAt();
        OffsetDateTime challengeCompletedAt = latestChallenge.getCompletedAt();
        if (quickReviewCompletedAt == null) {
            return latestChallenge;
        }
        if (challengeCompletedAt == null) {
            return latestQuickReview;
        }

        return challengeCompletedAt.isAfter(quickReviewCompletedAt) ? latestChallenge : latestQuickReview;
    }

    private QuickReviewSessionEntity fetchLatestCompletedSession(
        UUID userId,
        UUID studyPackId,
        QuickReviewSessionMode sessionMode
    ) {
        return quickReviewSessionRepository
            .findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                userId,
                studyPackId,
                sessionMode,
                PageRequest.of(0, 1)
            ).stream()
            .findFirst()
            .orElse(null);
    }
}
