package com.studysnap.backend.service;

import com.studysnap.backend.dto.ContinueStudyingReason;
import com.studysnap.backend.dto.ContinueStudyingResumeState;
import com.studysnap.backend.dto.ContinueStudyingResponse;
import com.studysnap.backend.dto.MasterySnapshotResponse;
import com.studysnap.backend.dto.StudyEngagementResponse;
import com.studysnap.backend.dto.TodayFocusResponse;
import com.studysnap.backend.dto.TodayFocusType;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserActivityEventEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.util.SummaryPreviewUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {
    private static final BigDecimal PERFECT_SCORE = BigDecimal.valueOf(100);
    private static final Set<ActivityType> MEANINGFUL_STUDY_ACTIVITIES = EnumSet.of(
            ActivityType.CREATED_STUDY_PACK,
            ActivityType.COMPLETED_QUICK_REVIEW,
            ActivityType.COMPLETED_ADAPTIVE_QUIZ
    );

    private final UserRepository userRepository;
    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final ActivityEventRepository activityEventRepository;
    private final SubscriptionService subscriptionService;
    private final FeatureGateService featureGateService;

    public ContinueStudyingResponse getContinueStudyingRecommendation(UUID userId) {
        // Priority 1: resume an unfinished Quick Review session when available.
        Optional<ContinueStudyingResponse> inProgress = resolveInProgressRecommendation(userId);
        if (inProgress.isPresent()) {
            return inProgress.get();
        }

        // Priority 2: otherwise recommend the weakest recently reviewed Study Pack.
        Optional<ContinueStudyingResponse> lowScoreRecent = resolveLowScoreRecentRecommendation(userId);
        if (lowScoreRecent.isPresent()) {
            return lowScoreRecent.get();
        }

        // Priority 3: otherwise use the most recently opened Study Pack.
        Optional<ContinueStudyingResponse> recentlyOpened = resolveRecentlyOpenedRecommendation(userId);
        if (recentlyOpened.isPresent()) {
            return recentlyOpened.get();
        }

        // Priority 4: otherwise use the most recently created Study Pack.
        Optional<StudyPackEntity> recentlyCreated = studyPackRepository.findTopByOwnerUserIdOrderByCreatedAtDesc(userId);
        if (recentlyCreated.isPresent()) {
            StudyPackEntity studyPack = recentlyCreated.get();
            return toResponse(
                    studyPack,
                    ContinueStudyingReason.RECENTLY_CREATED,
                    null,
                    null,
                    findLastOpenedAt(userId, studyPack.getId()),
                    studyPack.getCreatedAt(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        // No Study Packs or usable activity context -> no recommendation.
        return new ContinueStudyingResponse(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public TodayFocusResponse getTodayFocus(UUID userId) {
        PlanType planType = subscriptionService.resolvePlan(userId);
        Optional<TodayFocusResponse> inProgressFocus = resolveTodayFocusInProgress(userId);
        if (inProgressFocus.isPresent()) {
            return inProgressFocus.get();
        }

        Optional<TodayFocusResponse> weakConceptFocus = resolveTodayFocusWeakConcepts(userId, planType);
        if (weakConceptFocus.isPresent()) {
            return weakConceptFocus.get();
        }

        Optional<TodayFocusResponse> reviewPackFocus = resolveTodayFocusReviewPack(userId);
        return reviewPackFocus.orElseGet(() -> new TodayFocusResponse(
                TodayFocusType.STUDY_SUGGESTION,
                null,
                "Start your first review",
                "Create your first Study Pack to begin your daily focus.",
                "Create Study Pack"
        ));

    }

    public StudyEngagementResponse getStudyEngagement(UUID userId) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return new StudyEngagementResponse(EngagementMode.FOCUSED, 0, 0, 0);
        }

        EngagementMode engagementMode = user.getEngagementMode() == null
                ? EngagementMode.FOCUSED
                : user.getEngagementMode();
        int studyDaysThisWeek = countStudyDaysThisWeek(userId);
        int currentStreak = user.getCurrentStreak() == null ? 0 : user.getCurrentStreak();
        int longestStreak = user.getLongestStreak() == null ? 0 : user.getLongestStreak();

        return new StudyEngagementResponse(engagementMode, currentStreak, longestStreak, studyDaysThisWeek);
    }

    public MasterySnapshotResponse getMasterySnapshot(UUID userId) {
        List<QuickReviewSessionEntity> recentCompletedSessions = quickReviewSessionRepository
                .findByUserIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        PageRequest.of(0, 30)
                )
                .stream()
                .filter(session -> session.getStatus() == QuickReviewSessionStatus.COMPLETED)
                .toList();

        if (recentCompletedSessions.isEmpty()) {
            return new MasterySnapshotResponse(null, null, 0);
        }

        List<BigDecimal> recentScores = recentCompletedSessions.stream()
                .map(this::scorePercentageOrZero)
                .toList();

        BigDecimal bestRecentScore = recentScores.stream()
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal averageRecentScore = recentScores.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(recentScores.size()), 2, RoundingMode.HALF_UP);

        int studyPacksReviewed = (int) recentCompletedSessions.stream()
                .map(QuickReviewSessionEntity::getStudyPackId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        return new MasterySnapshotResponse(
                averageRecentScore,
                bestRecentScore,
                studyPacksReviewed
        );
    }

    private Optional<TodayFocusResponse> resolveTodayFocusInProgress(UUID userId) {
        Optional<QuickReviewSessionEntity> inProgress = quickReviewSessionRepository
                .findTopByUserIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                        userId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        QuickReviewSessionStatus.IN_PROGRESS
                );
        if (inProgress.isEmpty()) {
            return Optional.empty();
        }

        QuickReviewSessionEntity session = inProgress.get();
        Optional<StudyPackEntity> studyPack = studyPackRepository.findByIdAndOwnerUserId(session.getStudyPackId(), userId);
        if (studyPack.isEmpty()) {
            return Optional.empty();
        }

        int currentQuestionIndex = session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex();
        int totalQuestions = session.getTotalQuestions() == null ? 0 : session.getTotalQuestions();
        ContinueStudyingResumeState resumeState = determineResumeState(session, currentQuestionIndex, totalQuestions);

        if (resumeState == ContinueStudyingResumeState.QUESTION_IN_PROGRESS) {
            int normalizedTotal = Math.max(1, totalQuestions);
            int normalizedQuestion = Math.min(Math.max(1, currentQuestionIndex + 1), normalizedTotal);
            return Optional.of(new TodayFocusResponse(
                    TodayFocusType.RESUME_REVIEW,
                    studyPack.get().getId().toString(),
                    "Resume Quick Review",
                    "You left off on Question " + normalizedQuestion + " of " + normalizedTotal + " in \""
                            + studyPack.get().getTitle() + "\".",
                    "Resume Review"
            ));
        }

        if (resumeState == ContinueStudyingResumeState.RETRY_IN_PROGRESS
                || resumeState == ContinueStudyingResumeState.RETRY_TRANSITION) {
            int retryQuestionsLeft = resumeState == ContinueStudyingResumeState.RETRY_TRANSITION
                    ? countRetryQuestionIndexes(session)
                    : calculateRemainingQuestions(session, currentQuestionIndex, totalQuestions);
            int normalizedRetryCount = Math.max(0, retryQuestionsLeft);
            String questionLabel = normalizedRetryCount == 1 ? "question" : "questions";
            String message = normalizedRetryCount > 0
                    ? "You still have " + normalizedRetryCount + " " + questionLabel + " to review in \""
                    + studyPack.get().getTitle() + "\"."
                    : "You still have missed questions ready to retry in \"" + studyPack.get().getTitle() + "\".";
            return Optional.of(new TodayFocusResponse(
                    TodayFocusType.RETRY_REVIEW,
                    studyPack.get().getId().toString(),
                    "Retry Incorrect Questions",
                    message,
                    "Retry Questions"
            ));
        }

        return Optional.empty();
    }

    private Optional<TodayFocusResponse> resolveTodayFocusWeakConcepts(UUID userId, PlanType planType) {
        if (!featureGateService.hasFeatureAccess(planType, Feature.WEAK_CONCEPT_DETECTION)) {
            return Optional.empty();
        }

        QuickReviewSessionEntity latestCompletedSession = quickReviewSessionRepository
                .findByUserIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        PageRequest.of(0, 1)
                )
                .stream()
                .findFirst()
                .orElse(null);
        if (latestCompletedSession == null) {
            return Optional.empty();
        }

        List<String> weakConcepts = extractWeakConcepts(latestCompletedSession);
        if (weakConcepts.isEmpty()) {
            return Optional.empty();
        }

        Optional<StudyPackEntity> studyPack = studyPackRepository.findByIdAndOwnerUserId(
                latestCompletedSession.getStudyPackId(),
                userId
        );
        if (studyPack.isEmpty()) {
            return Optional.empty();
        }

        int weakConceptCount = weakConcepts.size();
        String conceptLabel = weakConceptCount == 1 ? "concept" : "concepts";
        return Optional.of(new TodayFocusResponse(
                TodayFocusType.PRACTICE_WEAK_CONCEPT,
                studyPack.get().getId().toString(),
                "Practice Weak Concepts",
                "Your latest Quick Review in \"" + studyPack.get().getTitle() + "\" showed " + weakConceptCount + " weak "
                        + conceptLabel + ". Practice them now.",
                "Practice Weak Areas"
        ));
    }

    private Optional<TodayFocusResponse> resolveTodayFocusReviewPack(UUID userId) {
        Optional<StudyPackEntity> fallbackStudyPack = findLastOpenedStudyPack(userId)
                .or(() -> findMostRecentlyCreatedStudyPack(userId))
                .or(() -> findMostRecentlyReviewedStudyPack(userId));

        if (fallbackStudyPack.isEmpty()) {
            return Optional.empty();
        }

        StudyPackEntity studyPack = fallbackStudyPack.get();
        return Optional.of(new TodayFocusResponse(
                TodayFocusType.REVIEW_PACK,
                studyPack.getId().toString(),
                "Reinforce \"" + studyPack.getTitle() + "\"",
                "A quick review today can strengthen your understanding.",
                "Start Quick Review"
        ));
    }

    private Optional<StudyPackEntity> findLastOpenedStudyPack(UUID userId) {
        List<UserActivityEventEntity> recentOpened = activityEventRepository
                .findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
                        userId,
                        ActivityType.OPENED_STUDY_PACK,
                        PageRequest.of(0, 30)
                );

        for (UserActivityEventEntity openedEvent : recentOpened) {
            UUID studyPackId = openedEvent.getStudyPackId();
            if (studyPackId == null) {
                continue;
            }
            Optional<StudyPackEntity> studyPack = studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId);
            if (studyPack.isPresent()) {
                return studyPack;
            }
        }

        return Optional.empty();
    }

    private Optional<StudyPackEntity> findMostRecentlyCreatedStudyPack(UUID userId) {
        return studyPackRepository.findTopByOwnerUserIdOrderByCreatedAtDesc(userId);
    }

    private Optional<StudyPackEntity> findMostRecentlyReviewedStudyPack(UUID userId) {
        List<QuickReviewSessionEntity> recentCompleted = quickReviewSessionRepository
                .findByUserIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        PageRequest.of(0, 30)
                );

        for (QuickReviewSessionEntity session : recentCompleted) {
            Optional<StudyPackEntity> studyPack = studyPackRepository.findByIdAndOwnerUserId(session.getStudyPackId(), userId);
            if (studyPack.isPresent()) {
                return studyPack;
            }
        }

        return Optional.empty();
    }

    private Optional<ContinueStudyingResponse> resolveInProgressRecommendation(UUID userId) {
        Optional<QuickReviewSessionEntity> inProgress = quickReviewSessionRepository
                .findTopByUserIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                        userId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        QuickReviewSessionStatus.IN_PROGRESS
                );
        if (inProgress.isEmpty()) {
            return Optional.empty();
        }

        QuickReviewSessionEntity session = inProgress.get();
        Optional<StudyPackEntity> studyPack = studyPackRepository.findByIdAndOwnerUserId(session.getStudyPackId(), userId);
        if (studyPack.isEmpty()) {
            return Optional.empty();
        }

        int currentQuestionIndex = session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex();
        int totalQuestions = session.getTotalQuestions() == null ? 0 : session.getTotalQuestions();
        QuickReviewRound currentRound = session.getCurrentRound();
        int remainingQuestions = calculateRemainingQuestions(session, currentQuestionIndex, totalQuestions);
        ContinueStudyingResumeState resumeState = determineResumeState(
                session,
                currentQuestionIndex,
                totalQuestions
        );
        return Optional.of(toResponse(
                studyPack.get(),
                ContinueStudyingReason.RESUME_REVIEW,
                null,
                session.getCreatedAt(),
                findLastOpenedAt(userId, session.getStudyPackId()),
                studyPack.get().getCreatedAt(),
                currentQuestionIndex,
                totalQuestions,
                currentRound,
                remainingQuestions,
                resumeState
        ));
    }

    private Optional<ContinueStudyingResponse> resolveLowScoreRecentRecommendation(UUID userId) {
        List<QuickReviewSessionEntity> recentSessions = quickReviewSessionRepository
                .findByUserIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        PageRequest.of(0, 50)
                );

        Map<UUID, QuickReviewSessionEntity> latestSessionByStudyPack = new LinkedHashMap<>();
        for (QuickReviewSessionEntity session : recentSessions) {
            if (session.getStatus() != QuickReviewSessionStatus.COMPLETED) {
                continue;
            }
            UUID studyPackId = session.getStudyPackId();
            if (!latestSessionByStudyPack.containsKey(studyPackId)) {
                latestSessionByStudyPack.put(studyPackId, session);
            }
        }

        List<QuickReviewSessionEntity> weakestCandidates = latestSessionByStudyPack.values().stream()
                .filter(session -> scorePercentageOrZero(session).compareTo(PERFECT_SCORE) < 0)
                .sorted(
                        Comparator.comparing(this::scorePercentageOrZero)
                                .thenComparing(
                                        QuickReviewSessionEntity::getCompletedAt,
                                        Comparator.nullsLast(Comparator.reverseOrder())
                                )
                )
                .toList();

        for (QuickReviewSessionEntity session : weakestCandidates) {
            Optional<StudyPackEntity> studyPack = studyPackRepository.findByIdAndOwnerUserId(session.getStudyPackId(), userId);
            if (studyPack.isEmpty()) {
                continue;
            }

            return Optional.of(toResponse(
                    studyPack.get(),
                    ContinueStudyingReason.LOW_SCORE_RECENT,
                    scorePercentageOrZero(session),
                    session.getCompletedAt(),
                    findLastOpenedAt(userId, session.getStudyPackId()),
                    studyPack.get().getCreatedAt(),
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        }

        return Optional.empty();
    }

    private BigDecimal scorePercentageOrZero(QuickReviewSessionEntity session) {
        return session.getScorePercentage() == null ? BigDecimal.ZERO : session.getScorePercentage();
    }

    private Optional<ContinueStudyingResponse> resolveRecentlyOpenedRecommendation(UUID userId) {
        List<UserActivityEventEntity> recentOpened = activityEventRepository
                .findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
                        userId,
                        ActivityType.OPENED_STUDY_PACK,
                        PageRequest.of(0, 30)
                );

        for (UserActivityEventEntity openedEvent : recentOpened) {
            UUID studyPackId = openedEvent.getStudyPackId();
            if (studyPackId == null) {
                continue;
            }

            Optional<StudyPackEntity> studyPack = studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId);
            if (studyPack.isEmpty()) {
                continue;
            }

            Optional<QuickReviewSessionEntity> latestSession = quickReviewSessionRepository
                    .findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                            userId,
                            studyPackId,
                            QuickReviewSessionMode.QUICK_REVIEW,
                            PageRequest.of(0, 1)
                    )
                    .stream()
                    .findFirst();

            return Optional.of(toResponse(
                    studyPack.get(),
                    ContinueStudyingReason.RECENTLY_OPENED,
                    latestSession.map(QuickReviewSessionEntity::getScorePercentage).orElse(null),
                    latestSession.map(QuickReviewSessionEntity::getCompletedAt).orElse(null),
                    openedEvent.getCreatedAt(),
                    studyPack.get().getCreatedAt(),
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        }

        return Optional.empty();
    }

    private OffsetDateTime findLastOpenedAt(UUID userId, UUID studyPackId) {
        return activityEventRepository
                .findTopByUserIdAndStudyPackIdAndActivityTypeOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        ActivityType.OPENED_STUDY_PACK
                )
                .map(UserActivityEventEntity::getCreatedAt)
                .orElse(null);
    }

    private ContinueStudyingResponse toResponse(
            StudyPackEntity studyPack,
            ContinueStudyingReason reason,
            BigDecimal lastScorePercentage,
            OffsetDateTime lastReviewedAt,
            OffsetDateTime lastOpenedAt,
            OffsetDateTime createdAt,
            Integer currentQuestionIndex,
            Integer totalQuestions,
            QuickReviewRound currentRound,
            Integer remainingQuestions,
            ContinueStudyingResumeState resumeState
    ) {
        return new ContinueStudyingResponse(
                studyPack.getId().toString(),
                studyPack.getTitle(),
                SummaryPreviewUtils.buildSummaryPreview(studyPack.getSummary(), 140),
                reason,
                lastScorePercentage,
                lastReviewedAt,
                lastOpenedAt,
                createdAt,
                currentQuestionIndex,
                totalQuestions,
                currentRound,
                remainingQuestions,
                resumeState
        );
    }

    private int calculateRemainingQuestions(
            QuickReviewSessionEntity session,
            int currentQuestionIndex,
            int totalQuestions
    ) {
        if (session.getCurrentRound() != QuickReviewRound.RETRY) {
            return 0;
        }
        if (session.getSessionState() == null) {
            return Math.max(0, totalQuestions - currentQuestionIndex);
        }
        Object retryQuestionIndexes = session.getSessionState().get("retryQuestionIndexes");
        if (!(retryQuestionIndexes instanceof List<?> retryIndexesList)) {
            return Math.max(0, totalQuestions - currentQuestionIndex);
        }
        long validRetryIndexes = retryIndexesList.stream()
                .filter(Integer.class::isInstance)
                .count();
        return Math.max(0, Math.toIntExact(validRetryIndexes) - currentQuestionIndex);
    }

    private ContinueStudyingResumeState determineResumeState(
            QuickReviewSessionEntity session,
            int currentQuestionIndex,
            int totalQuestions
    ) {
        if (session.getCurrentRound() == QuickReviewRound.RETRY) {
            return ContinueStudyingResumeState.RETRY_IN_PROGRESS;
        }

        if (isRetryTransition(session, currentQuestionIndex, totalQuestions)) {
            return ContinueStudyingResumeState.RETRY_TRANSITION;
        }

        return ContinueStudyingResumeState.QUESTION_IN_PROGRESS;
    }

    private boolean isRetryTransition(
            QuickReviewSessionEntity session,
            int currentQuestionIndex,
            int totalQuestions
    ) {
        if (session.getCurrentRound() != QuickReviewRound.INITIAL) {
            return false;
        }
        if (session.getRetryCount() == null || session.getRetryCount() <= 0) {
            return false;
        }
        if (countRetryQuestionIndexes(session) <= 0) {
            return false;
        }
        return currentQuestionIndex >= Math.max(0, totalQuestions);
    }

    private int countRetryQuestionIndexes(QuickReviewSessionEntity session) {
        if (session.getSessionState() == null) {
            return 0;
        }
        Object retryQuestionIndexes = session.getSessionState().get("retryQuestionIndexes");
        if (!(retryQuestionIndexes instanceof List<?> retryIndexesList)) {
            return 0;
        }
        return (int) retryIndexesList.stream()
                .filter(Integer.class::isInstance)
                .count();
    }

    private List<String> extractWeakConcepts(QuickReviewSessionEntity session) {
        if (session.getSessionMetadata() == null) {
            return List.of();
        }
        Object weakConceptsRaw = session.getSessionMetadata().get("weakConcepts");
        if (!(weakConceptsRaw instanceof List<?> weakConceptsList)) {
            return List.of();
        }

        return weakConceptsList.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private int countStudyDaysThisWeek(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        OffsetDateTime weekStartAtMidnight = weekStart
                .atStartOfDay(ZoneId.systemDefault())
                .toOffsetDateTime();

        List<UserActivityEventEntity> events = activityEventRepository.findByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                userId,
                MEANINGFUL_STUDY_ACTIVITIES,
                weekStartAtMidnight
        );

        return (int) events.stream()
                .map(UserActivityEventEntity::getCreatedAt)
                .filter(Objects::nonNull)
                .map(createdAt -> createdAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate())
                .distinct()
                .count();
    }
}
