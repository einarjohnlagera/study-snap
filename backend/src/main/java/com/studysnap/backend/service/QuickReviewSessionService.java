package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuickReviewSessionCompleteRequest;
import com.studysnap.backend.dto.QuickReviewPerformanceSummaryResponse;
import com.studysnap.backend.dto.ChallengeQuizConceptStatResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.QuizSessionReviewResponse;
import com.studysnap.backend.dto.QuickReviewSessionProgressRequest;
import com.studysnap.backend.dto.QuickReviewSessionResponse;
import com.studysnap.backend.dto.QuickReviewSessionStartResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewConfidenceLevel;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.QuickReviewSessionNotFoundException;
import com.studysnap.backend.exception.StudyPackNotFoundException;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.util.QuizSessionReviewUtils;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class QuickReviewSessionService {
    private static final String SESSION_NOT_IN_PROGRESS_CODE = "SESSION_NOT_IN_PROGRESS";
    private static final String QUICK_REVIEW_SESSION_NOT_IN_PROGRESS_MESSAGE = "Quick Review session is already completed.";
    private static final String QUICK_REVIEW_SESSION_ALREADY_ENDED_MESSAGE = "Quick Review session has already ended.";
    private static final String QUICK_REVIEW_SESSION_FORFEITED_MESSAGE = "Quick Review session forfeited.";
    private static final String SESSION_REVIEW_NOT_AVAILABLE_CODE = "SESSION_REVIEW_NOT_AVAILABLE";
    private static final String QUICK_REVIEW_SESSION_REVIEW_NOT_AVAILABLE_MESSAGE = "Quick Review session review is only available after completion.";
    private static final String SESSION_STATE_SELECTED_CHOICES = "selectedChoices";
    private static final String SESSION_STATE_SELECTED_MULTI_CHOICES = "selectedMultiChoices";

    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final StudyPackRepository studyPackRepository;
    private final ActivityTrackingService activityTrackingService;
    private final AnalyticsService analyticsService;
    private final SubscriptionService subscriptionService;
    private final FeatureGateService featureGateService;
    private final ConceptHealthService conceptHealthService;

    public QuickReviewSessionStartResponse startSession(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)
                .orElseThrow(StudyPackNotFoundException::new);

        QuickReviewSessionEntity existing = quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        QuickReviewSessionStatus.IN_PROGRESS
                )
                .orElse(null);
        if (existing != null) {
            return toStartResponse(existing);
        }

        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(studyPack.getNoteId());
        session.setSessionMode(QuickReviewSessionMode.QUICK_REVIEW);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(studyPack.getQuiz() == null ? 0 : studyPack.getQuiz().size());
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        session.setRetryCount(0);
        session.setSessionState(null);
        session.setCreatedAt(OffsetDateTime.now());
        session.setCompletedAt(null);
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);

        activityTrackingService.recordActivity(userId, ActivityType.STARTED_QUICK_REVIEW, studyPackId);
        analyticsService.trackEvent(userId, AnalyticsEventType.QUICK_REVIEW_STARTED, studyPackId, Map.of(
                "sessionId", saved.getId().toString()
        ));

        return toStartResponse(saved);
    }

    @Transactional(readOnly = true)
    public QuickReviewSessionStartResponse getInProgressSession(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(StudyPackNotFoundException::new);

        return quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        QuickReviewSessionStatus.IN_PROGRESS
                )
                .map(this::toStartResponse)
                .orElse(new QuickReviewSessionStartResponse(null, null, 0, null, 0, null));
    }

    public QuickReviewSessionResponse updateSessionProgress(
            String sessionIdRaw,
            UUID userId,
            QuickReviewSessionProgressRequest request
    ) {
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(sessionIdRaw, QuickReviewSessionNotFoundException::new);
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.QUICK_REVIEW
                )
                .orElseThrow(QuickReviewSessionNotFoundException::new);

        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            throw new AppException(
                    SESSION_NOT_IN_PROGRESS_CODE,
                    QUICK_REVIEW_SESSION_NOT_IN_PROGRESS_MESSAGE,
                    HttpStatus.BAD_REQUEST
            );
        }

        session.setCurrentQuestionIndex(request.currentQuestionIndex());
        session.setCurrentRound(request.currentRound());
        session.setRetryCount(request.retryCount());
        session.setSessionState(request.sessionState());
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);

        return toResponse(saved, subscriptionService.resolvePlan(userId));
    }

    public QuickReviewSessionResponse completeSession(String sessionIdRaw, UUID userId, QuickReviewSessionCompleteRequest request) {
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(sessionIdRaw, QuickReviewSessionNotFoundException::new);
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.QUICK_REVIEW
                )
                .orElseThrow(QuickReviewSessionNotFoundException::new);

        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            throw new AppException(
                    SESSION_NOT_IN_PROGRESS_CODE,
                    QUICK_REVIEW_SESSION_ALREADY_ENDED_MESSAGE,
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.correctAnswers() > request.totalQuestions()) {
            throw new AppException(
                    "INVALID_SESSION_RESULT",
                    "Correct answers cannot exceed total questions.",
                    HttpStatus.BAD_REQUEST
            );
        }

        BigDecimal scorePercentage = BigDecimal.valueOf(request.correctAnswers())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(request.totalQuestions()), 2, RoundingMode.HALF_UP);

        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(request.totalQuestions());
        session.setCurrentRound(request.retryCount() > 0 ? QuickReviewRound.RETRY : QuickReviewRound.INITIAL);
        session.setTotalQuestions(request.totalQuestions());
        session.setCorrectAnswers(request.correctAnswers());
        session.setScorePercentage(scorePercentage);
        session.setRetryCount(request.retryCount());
        session.setDurationSeconds(request.durationSeconds());
        PlanType planType = subscriptionService.resolvePlan(userId);
        session.setSessionMetadata(sanitizeSessionMetadataForPlan(request.sessionMetadata(), planType));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        session.setCompletedAt(now);
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        List<String> correctConcepts = QuizSessionReviewUtils.computeFullyCorrectConcepts(
                computeConceptBreakdownForCompletion(saved, userId)
        );
        if (!correctConcepts.isEmpty()) {
            conceptHealthService.recordCorrectAnswers(userId, saved.getStudyPackId(), correctConcepts, now);
        }

        activityTrackingService.recordActivity(userId, ActivityType.COMPLETED_QUICK_REVIEW, saved.getStudyPackId());

        return toResponse(saved, planType);
    }

    public SimpleMessageResponse forfeitSession(String sessionIdRaw, UUID userId) {
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(sessionIdRaw, QuickReviewSessionNotFoundException::new);
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.QUICK_REVIEW
                )
                .orElseThrow(QuickReviewSessionNotFoundException::new);

        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            return new SimpleMessageResponse(QUICK_REVIEW_SESSION_ALREADY_ENDED_MESSAGE);
        }

        session.setStatus(QuickReviewSessionStatus.FORFEITED);
        session.setCompletedAt(null);
        quickReviewSessionRepository.save(session);
        return new SimpleMessageResponse(QUICK_REVIEW_SESSION_FORFEITED_MESSAGE);
    }

    public QuickReviewSessionResponse saveConfidenceLevel(
            String sessionIdRaw,
            UUID userId,
            QuickReviewConfidenceLevel confidenceLevel
    ) {
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(sessionIdRaw, QuickReviewSessionNotFoundException::new);
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.QUICK_REVIEW
                )
                .orElseThrow(QuickReviewSessionNotFoundException::new);

        if (session.getStatus() != QuickReviewSessionStatus.COMPLETED) {
            throw new AppException(
                    "SESSION_NOT_COMPLETED",
                    "Quick Review session must be completed before saving confidence feedback.",
                    HttpStatus.BAD_REQUEST
            );
        }

        session.setConfidenceLevel(confidenceLevel);
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        PlanType planType = subscriptionService.resolvePlan(userId);
        return toResponse(saved, planType);
    }

    @Transactional(readOnly = true)
    public QuizSessionReviewResponse getSessionReview(String studyPackIdRaw, String sessionIdRaw, UUID userId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(StudyPackNotFoundException::new);
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(sessionIdRaw, QuickReviewSessionNotFoundException::new);
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.QUICK_REVIEW
                )
                .orElseThrow(QuickReviewSessionNotFoundException::new);

        if (!studyPackId.equals(session.getStudyPackId())) {
            throw new QuickReviewSessionNotFoundException();
        }
        if (session.getCompletedAt() == null) {
            throw new AppException(
                    SESSION_REVIEW_NOT_AVAILABLE_CODE,
                    QUICK_REVIEW_SESSION_REVIEW_NOT_AVAILABLE_MESSAGE,
                    HttpStatus.BAD_REQUEST
            );
        }

        List<QuizItem> quiz = studyPack.getQuiz() == null ? List.of() : studyPack.getQuiz();
        Map<Integer, Integer> selectedChoices = QuizSessionStateUtils.extractSelectedChoiceIndexes(session.getSessionState(), quiz);
        Map<Integer, List<Integer>> selectedMultiChoices = QuizSessionStateUtils.extractSelectedMultiChoiceIndexes(session.getSessionState(), quiz);
        List<com.studysnap.backend.dto.ChallengeQuizConceptStatResponse> conceptBreakdown =
                QuizSessionReviewUtils.computeConceptBreakdown(quiz, selectedChoices, selectedMultiChoices);
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
                selectedMultiChoices,
                session.getCreatedAt(),
                session.getCompletedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<QuickReviewSessionResponse> listRecentSessions(String studyPackIdRaw, UUID userId, int limit) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(StudyPackNotFoundException::new);

        int normalizedLimit = Math.clamp(limit, 1, 10);
        PlanType planType = subscriptionService.resolvePlan(userId);
        return quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        PageRequest.of(0, normalizedLimit)
                ).stream()
                .map(session -> toResponse(session, planType))
                .toList();
    }

    @Transactional(readOnly = true)
    public QuickReviewPerformanceSummaryResponse getPerformanceSummary(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(StudyPackNotFoundException::new);

        long attempts = quickReviewSessionRepository.countByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNull(
                userId,
                studyPackId,
                QuickReviewSessionMode.QUICK_REVIEW
        );
        if (attempts == 0) {
            return new QuickReviewPerformanceSummaryResponse(null, 0L, null, null);
        }

        BigDecimal bestScore = quickReviewSessionRepository.findBestScorePercentageByUserIdAndStudyPackIdAndSessionMode(
                userId,
                studyPackId,
                QuickReviewSessionMode.QUICK_REVIEW
        );
        QuickReviewSessionEntity latest = quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        PageRequest.of(0, 1)
                ).stream()
                .findFirst()
                .orElse(null);

        return new QuickReviewPerformanceSummaryResponse(
                bestScore,
                attempts,
                latest == null ? null : latest.getScorePercentage(),
                latest == null ? null : latest.getCompletedAt()
        );
    }

    private QuickReviewSessionResponse toResponse(QuickReviewSessionEntity session, PlanType planType) {
        return new QuickReviewSessionResponse(
                session.getId().toString(),
                session.getStudyPackId().toString(),
                session.getStatus(),
                session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex(),
                session.getCurrentRound(),
                session.getTotalQuestions() == null ? 0 : session.getTotalQuestions(),
                session.getCorrectAnswers() == null ? 0 : session.getCorrectAnswers(),
                session.getScorePercentage() == null ? BigDecimal.ZERO : session.getScorePercentage(),
                session.getRetryCount() == null ? 0 : session.getRetryCount(),
                session.getDurationSeconds(),
                session.getConfidenceLevel(),
                featureGateService.hasFeatureAccess(planType, Feature.WEAK_CONCEPT_DETECTION) ? extractWeakConcepts(session) : List.of(),
                session.getSessionState(),
                session.getCreatedAt(),
                session.getCompletedAt()
        );
    }

    private QuickReviewSessionStartResponse toStartResponse(QuickReviewSessionEntity session) {
        return new QuickReviewSessionStartResponse(
                session.getId().toString(),
                session.getStatus(),
                session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex(),
                session.getCurrentRound(),
                session.getRetryCount() == null ? 0 : session.getRetryCount(),
                session.getSessionState()
        );
    }

    private List<ChallengeQuizConceptStatResponse> computeConceptBreakdownForCompletion(
            QuickReviewSessionEntity session,
            UUID userId
    ) {
        if (!hasStoredSelections(session.getSessionState())) {
            return List.of();
        }

        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        if (quiz.isEmpty()) {
            StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserId(session.getStudyPackId(), userId)
                    .orElseThrow(StudyPackNotFoundException::new);
            quiz = studyPack.getQuiz() == null ? List.of() : studyPack.getQuiz();
        }
        Map<Integer, Integer> selectedChoices = QuizSessionStateUtils.extractSelectedChoiceIndexes(session.getSessionState(), quiz);
        Map<Integer, List<Integer>> selectedMultiChoices = QuizSessionStateUtils.extractSelectedMultiChoiceIndexes(session.getSessionState(), quiz);
        if (selectedChoices.isEmpty() && selectedMultiChoices.isEmpty()) {
            return List.of();
        }
        return QuizSessionReviewUtils.computeConceptBreakdown(quiz, selectedChoices, selectedMultiChoices);
    }

    private boolean hasStoredSelections(Map<String, Object> sessionState) {
        return sessionState != null
                && (sessionState.containsKey(SESSION_STATE_SELECTED_CHOICES)
                || sessionState.containsKey(SESSION_STATE_SELECTED_MULTI_CHOICES));
    }

    private List<String> extractWeakConcepts(QuickReviewSessionEntity session) {
        if (session.getSessionMetadata() == null) {
            return List.of();
        }
        Object weakConceptsRaw = session.getSessionMetadata().get("weakConcepts");
        if (!(weakConceptsRaw instanceof List<?> weakConceptsList)) {
            return List.of();
        }

        List<String> weakConcepts = new ArrayList<>();
        for (Object value : weakConceptsList) {
            if (!(value instanceof String concept)) {
                continue;
            }
            String normalized = concept.trim();
            if (!normalized.isBlank()) {
                weakConcepts.add(normalized);
            }
        }
        return weakConcepts;
    }

    private Map<String, Object> sanitizeSessionMetadataForPlan(
            Map<String, Object> sessionMetadata,
            PlanType planType
    ) {
        if (sessionMetadata == null || sessionMetadata.isEmpty()) {
            return sessionMetadata;
        }
        if (featureGateService.hasFeatureAccess(planType, Feature.WEAK_CONCEPT_DETECTION)) {
            return sessionMetadata;
        }

        Map<String, Object> sanitized = new LinkedHashMap<>(sessionMetadata);
        sanitized.remove("weakConcepts");
        return sanitized.isEmpty() ? null : sanitized;
    }
}
