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
import com.studysnap.backend.model.StudyPackProgressProjection;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackLatestCompletionProjection;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.service.model.StudyPackQuizMastery;
import com.studysnap.backend.util.QuizSessionReviewUtils;
import com.studysnap.backend.util.QuizSessionStateUtils;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class QuickReviewSessionService {
    private static final Logger log = LoggerFactory.getLogger(QuickReviewSessionService.class);
    private static final String SESSION_NOT_IN_PROGRESS_CODE = "SESSION_NOT_IN_PROGRESS";
    private static final String QUICK_REVIEW_SESSION_NOT_IN_PROGRESS_MESSAGE = "Quick Review session is already completed.";
    private static final String QUICK_REVIEW_SESSION_ALREADY_ENDED_MESSAGE = "Quick Review session has already ended.";
    private static final String QUICK_REVIEW_SESSION_FORFEITED_MESSAGE = "Quick Review session forfeited.";
    private static final String SESSION_REVIEW_NOT_AVAILABLE_CODE = "SESSION_REVIEW_NOT_AVAILABLE";
    private static final String QUICK_REVIEW_SESSION_REVIEW_NOT_AVAILABLE_MESSAGE = "Quick Review session review is only available after completion.";
    private static final String ANALYTICS_METADATA_SESSION_ID = "sessionId";
    private static final String ANALYTICS_METADATA_RETRY_COUNT = "retryCount";
    private static final String ANALYTICS_METADATA_MASTERY_PATH = "masteryPath";
    private static final String ANALYTICS_METADATA_PRIOR_STATE_KNOWN = "priorMasteryStateKnown";
    private static final String MASTERY_PATH_FIRST_PASS = "FIRST_PASS";
    private static final String MASTERY_PATH_AFTER_RETRY = "AFTER_RETRY";

    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final StudyPackRepository studyPackRepository;
    private final ActivityEventRepository activityEventRepository;
    private final ActivityTrackingService activityTrackingService;
    private final AnalyticsService analyticsService;
    private final SubscriptionService subscriptionService;
    private final FeatureGateService featureGateService;
    private final ConceptHealthService conceptHealthService;
    private final StudyPackQuizMasteryService studyPackQuizMasteryService;
    private final NoteShareService noteShareService;


    public QuickReviewSessionStartResponse startSession(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        StudyPackEntity studyPack = findAccessibleStudyPack(studyPackId, userId, true);

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
        findAccessibleStudyPack(studyPackId, userId, false);

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
        recheckMaterialAccess(session.getStudyPackId(), userId);

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
        // Resolve material access ONCE and carry the result down. Authorization still happens on every
        // call — it is what cuts a revoked recipient off mid-session — but re-reading the same pack row
        // later in the same request bought nothing and multiplied reads on the busiest write path.
        Optional<StudyPackEntity> accessibleStudyPack = recheckMaterialAccess(session.getStudyPackId(), userId);

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

        boolean isFirstCompletedSessionEver = !quickReviewSessionRepository
                .existsByUserIdAndStatusAndCompletedAtIsNotNull(userId, QuickReviewSessionStatus.COMPLETED);
        boolean isSecondCompletedSessionEver = quickReviewSessionRepository
                .countByUserIdAndStatusAndCompletedAtIsNotNull(userId, QuickReviewSessionStatus.COMPLETED) == 1;
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
        StudyPackEntity studyPack = null;
        List<ChallengeQuizConceptStatResponse> conceptBreakdown = List.of();
        Optional<StudyPackQuizMastery> masteryBeforeCompletion = Optional.empty();
        try {
            studyPack = accessibleStudyPack.orElse(null);
            if (studyPack != null && studyPack.getQuiz() != null && !studyPack.getQuiz().isEmpty()) {
                masteryBeforeCompletion = studyPackQuizMasteryService.tryResolve(userId, studyPack);
                conceptBreakdown = QuizSessionReviewUtils.computeConceptBreakdownForStoredSelections(
                        studyPack.getQuiz(),
                        session.getSessionState()
                );
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "action=derive_quick_review_concept_breakdown outcome=failed userId={} studyPackId={} sessionId={}",
                    userId,
                    session.getStudyPackId(),
                    session.getId(),
                    exception
            );
        }
        if (session.getSessionMode() == QuickReviewSessionMode.QUICK_REVIEW
                && studyPack != null
                && studyPack.getQuiz() != null
                && !studyPack.getQuiz().isEmpty()
                && !conceptBreakdown.isEmpty()) {
            int verifiedCorrectAnswers = conceptBreakdown.stream()
                    .mapToInt(ChallengeQuizConceptStatResponse::correctAnswers)
                    .sum();
            session.setVerifiedCorrectAnswers(verifiedCorrectAnswers);
        }
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        boolean verifiedPerfect = studyPack != null
                && studyPack.getQuiz() != null
                && !studyPack.getQuiz().isEmpty()
                && saved.getVerifiedCorrectAnswers() != null
                && saved.getVerifiedCorrectAnswers() == studyPack.getQuiz().size();
        if (verifiedPerfect) {
            Map<String, Object> masteryMetadata = Map.of(
                    ANALYTICS_METADATA_SESSION_ID, saved.getId().toString(),
                    ANALYTICS_METADATA_RETRY_COUNT, saved.getRetryCount() == null ? 0 : saved.getRetryCount(),
                    ANALYTICS_METADATA_MASTERY_PATH,
                    saved.getRetryCount() == null || saved.getRetryCount() == 0
                            ? MASTERY_PATH_FIRST_PASS
                            : MASTERY_PATH_AFTER_RETRY
            );
            trackAnalyticsSafely(
                    userId,
                    AnalyticsEventType.QUICK_REVIEW_MASTERED,
                    saved.getStudyPackId(),
                    masteryMetadata
            );
            // Fire on a KNOWN first transition, and also when the prior state is UNKNOWN because the
            // lookup failed. The asymmetry is deliberate and differs from the Challenge launch split:
            // this is a once-per-(user, Study Pack) transition, so a dropped event is unrecoverable,
            // whereas a duplicate is recoverable at query time by taking the earliest per user+pack.
            // Treating "unknown" as "already mastered" silently loses the metric this release exists
            // to produce.
            boolean alreadyMastered = masteryBeforeCompletion
                    .map(StudyPackQuizMastery::mastered)
                    .orElse(false);
            if (!alreadyMastered) {
                Map<String, Object> unlockMetadata = new LinkedHashMap<>(masteryMetadata);
                unlockMetadata.put(
                        ANALYTICS_METADATA_PRIOR_STATE_KNOWN,
                        masteryBeforeCompletion.isPresent()
                );
                trackAnalyticsSafely(
                        userId,
                        AnalyticsEventType.STUDY_PACK_QUIZ_UNLOCKED,
                        saved.getStudyPackId(),
                        unlockMetadata
                );
            }
        }

        List<String> correctConcepts = QuizSessionReviewUtils.computeFullyCorrectConcepts(conceptBreakdown);
        List<String> missedConcepts = QuizSessionReviewUtils.computeConceptsWithMisses(conceptBreakdown);
        if (!correctConcepts.isEmpty()) {
            conceptHealthService.recordCorrectAnswers(userId, saved.getStudyPackId(), correctConcepts, now);
        }
        List<String> twiceMissedConcepts = List.of();
        if (!missedConcepts.isEmpty()) {
            twiceMissedConcepts = conceptHealthService.recordIncorrectAnswers(
                    userId,
                    saved.getStudyPackId(),
                    missedConcepts,
                    now
            );
        }

        boolean isFirstCompletedQuiz = !activityEventRepository.existsByUserIdAndActivityTypeIn(
                userId,
                Set.of(ActivityType.COMPLETED_QUICK_REVIEW, ActivityType.COMPLETED_CHALLENGE_QUIZ)
        );
        activityTrackingService.recordActivity(userId, ActivityType.COMPLETED_QUICK_REVIEW, saved.getStudyPackId());

        return toResponse(
                saved,
                planType,
                isFirstCompletedQuiz,
                isFirstCompletedSessionEver,
                isSecondCompletedSessionEver,
                twiceMissedConcepts
        );
    }

    private void trackAnalyticsSafely(
            UUID userId,
            AnalyticsEventType eventType,
            UUID studyPackId,
            Map<String, Object> metadata
    ) {
        try {
            analyticsService.trackEvent(userId, eventType, studyPackId, metadata);
        } catch (RuntimeException exception) {
            log.warn(
                    "action=track_quick_review_analytics outcome=failed userId={} studyPackId={} eventType={}",
                    userId,
                    studyPackId,
                    eventType,
                    exception
            );
        }
    }

    public SimpleMessageResponse forfeitSession(String sessionIdRaw, UUID userId) {
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(sessionIdRaw, QuickReviewSessionNotFoundException::new);
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.QUICK_REVIEW
                )
                .orElseThrow(QuickReviewSessionNotFoundException::new);
        recheckMaterialAccess(session.getStudyPackId(), userId);

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
        recheckMaterialAccess(session.getStudyPackId(), userId);

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
        StudyPackEntity studyPack = findAccessibleStudyPack(studyPackId, userId, false);
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
                Map.of(),
                Map.of(),
                session.getCreatedAt(),
                session.getCompletedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<QuickReviewSessionResponse> listRecentSessions(String studyPackIdRaw, UUID userId, int limit) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(studyPackIdRaw, StudyPackNotFoundException::new);
        findAccessibleStudyPack(studyPackId, userId, false);

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
        findAccessibleStudyPack(studyPackId, userId, false);

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

    private StudyPackEntity findAccessibleStudyPack(UUID studyPackId, UUID userId, boolean forUpdate) {
        Optional<StudyPackEntity> owned = forUpdate
                ? studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)
                : studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId);
        if (owned.isPresent()) {
            return owned.get();
        }
        // Not the owner: the pack may still be SHARED with this caller. Authorizing here re-verifies the
        // relationship is ACCEPTED on every call, which is what makes a revoke — or a birth-year correction
        // that reverts the link to PENDING — cut practice access on the very next request.
        noteShareService.requireSharedStudyPackAccess(userId, studyPackId);
        return studyPackRepository.findById(studyPackId).orElseThrow(StudyPackNotFoundException::new);
    }

    private Optional<StudyPackEntity> recheckMaterialAccess(UUID studyPackId, UUID userId) {
        // ⚠️ Unconditional by design. Access is re-derived on EVERY call, never cached for the life of a
        // session — a recipient whose share or relationship is revoked mid-session must not be able to keep
        // answering. Guarding this behind a nullable dependency would silently turn it into a no-op, which
        // is exactly what it did before: every pre-existing test constructed this service with a null
        // NoteShareService, so the recheck never ran in the suite at all.
        try {
            return findVisibleStudyPack(studyPackId, userId);
        } catch (AppException denied) {
            // An authorization RESULT. Must propagate — swallowing it is the whole failure mode this guards.
            throw denied;
        } catch (RuntimeException readFault) {
            // An infrastructure fault reading the pack (a corrupt row, an unparseable state) is NOT a denial.
            // Completion has always tolerated a pack it cannot read, and the caller owns the session either
            // way, so failing here would strand a learner's own session over something unrelated to sharing.
            log.warn(
                    "action=recheck_material_access outcome=read_failed userId={} studyPackId={}",
                    userId,
                    studyPackId,
                    readFault
            );
            return Optional.empty();
        }
    }

    /**
     * Owner-or-recipient lookup that stays tolerant of a MISSING pack.
     *
     * <p>⚠️ The empty case must not throw. Completing or forfeiting a session whose Study Pack has since
     * been deleted has always succeeded — the caller owns the SESSION, which was loaded separately, and the
     * pack is only needed to derive the concept breakdown. Denying it here would strand the learner's own
     * session for a reason that has nothing to do with sharing. A pack that EXISTS but is not owned is the
     * only case that needs a share, and that is the case this enforces.
     */
    private Optional<StudyPackEntity> findVisibleStudyPack(UUID studyPackId, UUID userId) {
        Optional<StudyPackEntity> owned = studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId);
        if (owned.isPresent()) {
            return owned;
        }
        Optional<StudyPackEntity> existing = studyPackRepository.findById(studyPackId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        noteShareService.requireSharedStudyPackAccess(userId, studyPackId);
        return existing;
    }

    @Transactional(readOnly = true)
    public Map<UUID, OffsetDateTime> getLastReviewedAtByNoteIds(Collection<UUID> noteIds, UUID userId) {
        if (noteIds == null || noteIds.isEmpty()) {
            return Map.of();
        }

        List<StudyPackProgressProjection> ownedStudyPacks = studyPackRepository
                .findProgressViewsByNoteIdIn(noteIds)
                .stream()
                .filter(studyPack -> userId.equals(studyPack.getOwnerUserId()))
                .toList();
        if (ownedStudyPacks.isEmpty()) {
            return Map.of();
        }

        Map<UUID, UUID> noteIdByStudyPackId = new HashMap<>();
        for (StudyPackProgressProjection studyPack : ownedStudyPacks) {
            noteIdByStudyPackId.put(studyPack.getId(), studyPack.getNoteId());
        }

        List<StudyPackLatestCompletionProjection> completions = quickReviewSessionRepository
                .findLatestCompletedAtByUserIdAndStudyPackIdInAndSessionMode(
                        userId,
                        QuickReviewSessionStatus.COMPLETED,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        noteIdByStudyPackId.keySet()
                );

        Map<UUID, OffsetDateTime> lastReviewedAtByNoteId = new HashMap<>();
        for (StudyPackLatestCompletionProjection completion : completions) {
            UUID noteId = noteIdByStudyPackId.get(completion.studyPackId());
            if (noteId != null && completion.completedAt() != null) {
                lastReviewedAtByNoteId.merge(noteId, completion.completedAt(),
                        (current, candidate) -> current.isAfter(candidate) ? current : candidate);
            }
        }
        return Map.copyOf(lastReviewedAtByNoteId);
    }

    private QuickReviewSessionResponse toResponse(QuickReviewSessionEntity session, PlanType planType) {
        return toResponse(session, planType, false, false, false, List.of());
    }

    private QuickReviewSessionResponse toResponse(
            QuickReviewSessionEntity session,
            PlanType planType,
            boolean isFirstCompletedQuiz,
            boolean isFirstCompletedSessionEver,
            boolean isSecondCompletedSessionEver,
            List<String> twiceMissedConcepts
    ) {
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
                session.getCompletedAt(),
                isFirstCompletedQuiz,
                isFirstCompletedSessionEver,
                isSecondCompletedSessionEver,
                twiceMissedConcepts
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
