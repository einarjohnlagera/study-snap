package com.studysnap.backend.service;

import com.studysnap.backend.dto.ContinueStudyingReason;
import com.studysnap.backend.dto.ContinueStudyingResumeState;
import com.studysnap.backend.dto.ContinueStudyingResponse;
import com.studysnap.backend.dto.ContinueStudyingResumeType;
import com.studysnap.backend.dto.DashboardConceptInsightResponse;
import com.studysnap.backend.dto.DashboardFocusAreasResponse;
import com.studysnap.backend.dto.DashboardOverviewResponse;
import com.studysnap.backend.dto.DashboardPerformanceSummaryResponse;
import com.studysnap.backend.dto.DashboardWeeklyActivityResponse;
import com.studysnap.backend.dto.MasterySnapshotResponse;
import com.studysnap.backend.dto.NotePerformanceSummaryResponse;
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
import com.studysnap.backend.repository.NoteRepository;
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
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {
    private static final BigDecimal PERFECT_SCORE = BigDecimal.valueOf(100);
    private static final Set<ActivityType> MEANINGFUL_STUDY_ACTIVITIES = EnumSet.of(
            ActivityType.CREATED_STUDY_PACK,
            ActivityType.STARTED_QUICK_REVIEW,
            ActivityType.STARTED_ADAPTIVE_PRACTICE,
            ActivityType.COMPLETED_QUICK_REVIEW,
            ActivityType.COMPLETED_CHALLENGE_QUIZ,
            ActivityType.COMPLETED_ADAPTIVE_QUIZ
    );

    private final UserRepository userRepository;
    private final StudyPackRepository studyPackRepository;
    private final NoteRepository noteRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final ActivityEventRepository activityEventRepository;
    private final SubscriptionService subscriptionService;
    private final FeatureGateService featureGateService;

    public ContinueStudyingResponse getContinueStudyingRecommendation(UUID userId) {
        // Priority 1: resume the latest unfinished review session when available.
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
                    null,
                    ContinueStudyingResumeType.QUICK_REVIEW
            );
        }

        // No Study Packs or usable activity context -> no recommendation.
        return new ContinueStudyingResponse(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
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

    public DashboardOverviewResponse getOverview(UUID userId) {
        PlanType planType = subscriptionService.resolvePlan(userId);
        List<QuickReviewSessionEntity> completedQuizSessions = quickReviewSessionRepository
                .findByUserIdAndSessionModeInAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        List.of(QuickReviewSessionMode.QUICK_REVIEW, QuickReviewSessionMode.CHALLENGE)
                );
        List<QuickReviewSessionEntity> completedChallengeSessions = quickReviewSessionRepository
                .findByUserIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        QuickReviewSessionMode.CHALLENGE
                );

        DashboardPerformanceSummaryResponse performanceSummary = buildPerformanceSummary(
                completedQuizSessions,
                completedChallengeSessions,
                studyPackRepository.countByOwnerUserId(userId)
        );
        DashboardFocusAreasResponse focusAreas = buildFocusAreas(completedChallengeSessions, planType);
        DashboardWeeklyActivityResponse weeklyActivity = buildWeeklyActivity(userId);

        return new DashboardOverviewResponse(
                performanceSummary,
                focusAreas,
                weeklyActivity
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
                    studyPack.get().getNoteId() == null ? null : studyPack.get().getNoteId().toString(),
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
                    studyPack.get().getNoteId() == null ? null : studyPack.get().getNoteId().toString(),
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
                studyPack.get().getNoteId() == null ? null : studyPack.get().getNoteId().toString(),
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
                studyPack.getNoteId() == null ? null : studyPack.getNoteId().toString(),
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
        for (QuickReviewSessionEntity session : findInProgressSessionsByRecency(userId)) {
            Optional<StudyPackEntity> studyPack = studyPackRepository.findByIdAndOwnerUserId(session.getStudyPackId(), userId);
            if (studyPack.isEmpty()) {
                continue;
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
                    resumeState,
                    resolveResumeType(session.getSessionMode())
            ));
        }
        return Optional.empty();
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
                    null,
                    ContinueStudyingResumeType.QUICK_REVIEW
            ));
        }

        return Optional.empty();
    }

    private BigDecimal scorePercentageOrZero(QuickReviewSessionEntity session) {
        return session.getScorePercentage() == null ? BigDecimal.ZERO : session.getScorePercentage();
    }

    private DashboardPerformanceSummaryResponse buildPerformanceSummary(
            List<QuickReviewSessionEntity> completedQuizSessions,
            List<QuickReviewSessionEntity> completedChallengeSessions,
            long studyPacksCreated
    ) {
        BigDecimal averageQuizScore = null;
        if (!completedQuizSessions.isEmpty()) {
            BigDecimal totalScore = completedQuizSessions.stream()
                    .map(this::scorePercentageOrZero)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            averageQuizScore = totalScore
                    .divide(BigDecimal.valueOf(completedQuizSessions.size()), 2, RoundingMode.HALF_UP);
        }

        Map<String, ConceptPerformanceAccumulator> conceptPerformance = aggregateConceptPerformance(completedChallengeSessions);
        DashboardConceptInsightResponse strongestConcept = conceptPerformance.values().stream()
                .sorted(
                        Comparator.comparing(ConceptPerformanceAccumulator::accuracyPercentage)
                                .reversed()
                                .thenComparing(ConceptPerformanceAccumulator::totalQuestions, Comparator.reverseOrder())
                                .thenComparing(ConceptPerformanceAccumulator::conceptName)
                )
                .map(ConceptPerformanceAccumulator::toInsight)
                .findFirst()
                .orElse(null);
        DashboardConceptInsightResponse weakestConcept = conceptPerformance.values().stream()
                .sorted(
                        Comparator.comparing(ConceptPerformanceAccumulator::accuracyPercentage)
                                .thenComparing(ConceptPerformanceAccumulator::totalQuestions, Comparator.reverseOrder())
                                .thenComparing(ConceptPerformanceAccumulator::conceptName)
                )
                .map(ConceptPerformanceAccumulator::toInsight)
                .findFirst()
                .orElse(null);

        return new DashboardPerformanceSummaryResponse(
                averageQuizScore,
                completedQuizSessions.size(),
                studyPacksCreated,
                strongestConcept,
                weakestConcept
        );
    }

    private DashboardFocusAreasResponse buildFocusAreas(
            List<QuickReviewSessionEntity> completedChallengeSessions,
            PlanType planType
    ) {
        Map<String, ConceptPerformanceAccumulator> conceptPerformance = aggregateConceptPerformance(completedChallengeSessions);
        List<ConceptPerformanceAccumulator> weakestConcepts = conceptPerformance.values().stream()
                .sorted(
                        Comparator.comparing(ConceptPerformanceAccumulator::accuracyPercentage)
                                .thenComparing(ConceptPerformanceAccumulator::totalQuestions, Comparator.reverseOrder())
                                .thenComparing(ConceptPerformanceAccumulator::latestCompletedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                )
                .limit(3)
                .toList();

        String practiceNoteId = weakestConcepts.stream()
                .map(ConceptPerformanceAccumulator::noteId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        return new DashboardFocusAreasResponse(
                weakestConcepts.stream()
                        .map(ConceptPerformanceAccumulator::toInsight)
                        .toList(),
                practiceNoteId,
                featureGateService.hasFeatureAccess(planType, Feature.ADAPTIVE_QUIZ)
        );
    }

    private DashboardWeeklyActivityResponse buildWeeklyActivity(UUID userId) {
        OffsetDateTime weekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZoneId.systemDefault())
                .toOffsetDateTime();
        OffsetDateTime weekEnd = weekStart.plusDays(7);

        List<UserActivityEventEntity> weeklyEvents = activityEventRepository.findByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                userId,
                MEANINGFUL_STUDY_ACTIVITIES,
                weekStart
        ).stream()
                .filter(event -> event.getCreatedAt() != null && event.getCreatedAt().isBefore(weekEnd))
                .toList();

        int studyPacksCreated = countEventsByType(weeklyEvents, ActivityType.CREATED_STUDY_PACK);
        int quizzesTaken = countEventsByType(weeklyEvents, ActivityType.COMPLETED_QUICK_REVIEW)
                + countEventsByType(weeklyEvents, ActivityType.COMPLETED_CHALLENGE_QUIZ);
        int adaptiveSessions = countEventsByType(weeklyEvents, ActivityType.STARTED_ADAPTIVE_PRACTICE);
        int studyDays = (int) weeklyEvents.stream()
                .map(UserActivityEventEntity::getCreatedAt)
                .filter(Objects::nonNull)
                .map(createdAt -> createdAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate())
                .distinct()
                .count();

        return new DashboardWeeklyActivityResponse(
                studyPacksCreated,
                quizzesTaken,
                adaptiveSessions,
                studyDays
        );
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
                    null,
                    ContinueStudyingResumeType.QUICK_REVIEW
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
            ContinueStudyingResumeState resumeState,
            ContinueStudyingResumeType resumeType
    ) {
        ContinueStudyingNoteMetadata noteMetadata = resolveNoteMetadata(studyPack);
        return new ContinueStudyingResponse(
                studyPack.getId().toString(),
                studyPack.getNoteId() == null ? null : studyPack.getNoteId().toString(),
                noteMetadata.noteTitle(),
                noteMetadata.subject(),
                noteMetadata.courseProgram(),
                SummaryPreviewUtils.buildSummaryPreview(studyPack.getSummary(), 140),
                resumeType,
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

    private List<QuickReviewSessionEntity> findInProgressSessionsByRecency(UUID userId) {
        return Stream.of(
                        QuickReviewSessionMode.QUICK_REVIEW,
                        QuickReviewSessionMode.CHALLENGE,
                        QuickReviewSessionMode.ADAPTIVE
                )
                .map(sessionMode -> quickReviewSessionRepository.findTopByUserIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                        userId,
                        sessionMode,
                        QuickReviewSessionStatus.IN_PROGRESS
                ).orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        QuickReviewSessionEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    private ContinueStudyingResumeType resolveResumeType(QuickReviewSessionMode sessionMode) {
        if (sessionMode == QuickReviewSessionMode.CHALLENGE) {
            return ContinueStudyingResumeType.CHALLENGE;
        }
        if (sessionMode == QuickReviewSessionMode.ADAPTIVE) {
            return ContinueStudyingResumeType.ADAPTIVE;
        }
        return ContinueStudyingResumeType.QUICK_REVIEW;
    }

    private ContinueStudyingNoteMetadata resolveNoteMetadata(StudyPackEntity studyPack) {
        UUID noteId = studyPack.getNoteId();
        if (noteId == null || studyPack.getOwnerUserId() == null) {
            return new ContinueStudyingNoteMetadata(studyPack.getTitle(), studyPack.getSubject(), null);
        }

        return noteRepository.findByIdAndOwnerUserId(noteId, studyPack.getOwnerUserId())
                .map(note -> new ContinueStudyingNoteMetadata(
                        normalizeOptionalText(note.getTitle(), studyPack.getTitle()),
                        normalizeOptionalText(note.getSubject(), studyPack.getSubject()),
                        normalizeOptionalText(note.getCourseProgram(), null)
                ))
                .orElseGet(() -> new ContinueStudyingNoteMetadata(studyPack.getTitle(), studyPack.getSubject(), null));
    }

    private String normalizeOptionalText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
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

    private Map<String, ConceptPerformanceAccumulator> aggregateConceptPerformance(List<QuickReviewSessionEntity> challengeSessions) {
        Map<String, ConceptPerformanceAccumulator> conceptPerformance = new LinkedHashMap<>();
        for (QuickReviewSessionEntity session : challengeSessions) {
            for (ConceptBreakdownEntry entry : extractConceptBreakdown(session)) {
                conceptPerformance.merge(
                        entry.conceptName(),
                        new ConceptPerformanceAccumulator(
                                entry.conceptName(),
                                entry.correctAnswers(),
                                entry.totalQuestions(),
                                session.getCompletedAt(),
                                session.getNoteId() == null ? null : session.getNoteId().toString()
                        ),
                        ConceptPerformanceAccumulator::merge
                );
            }
        }
        return conceptPerformance;
    }

    private List<ConceptBreakdownEntry> extractConceptBreakdown(QuickReviewSessionEntity session) {
        if (session.getSessionMetadata() == null) {
            return List.of();
        }
        Object conceptBreakdownRaw = session.getSessionMetadata().get("conceptBreakdown");
        if (!(conceptBreakdownRaw instanceof List<?> conceptBreakdownList)) {
            return List.of();
        }

        List<ConceptBreakdownEntry> entries = new ArrayList<>(conceptBreakdownList.size());
        for (Object entryRaw : conceptBreakdownList) {
            if (!(entryRaw instanceof Map<?, ?> entry)) {
                continue;
            }
            String conceptName = normalizeConceptName(entry.get("concept"));
            int correctAnswers = asInt(entry.get("correctAnswers"));
            int totalQuestions = asInt(entry.get("totalQuestions"));
            if (conceptName == null || totalQuestions <= 0) {
                continue;
            }
            entries.add(new ConceptBreakdownEntry(conceptName, correctAnswers, totalQuestions));
        }
        return entries;
    }

    private String normalizeConceptName(Object rawValue) {
        if (!(rawValue instanceof String value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private int asInt(Object rawValue) {
        if (rawValue instanceof Integer value) {
            return value;
        }
        if (rawValue instanceof Long value) {
            return Math.toIntExact(value);
        }
        if (rawValue instanceof Double value) {
            return (int) Math.round(value);
        }
        if (rawValue instanceof BigDecimal value) {
            return value.intValue();
        }
        return 0;
    }

    private int countEventsByType(List<UserActivityEventEntity> events, ActivityType activityType) {
        return (int) events.stream()
                .filter(event -> event.getActivityType() == activityType)
                .count();
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

    private record ConceptBreakdownEntry(
            String conceptName,
            int correctAnswers,
            int totalQuestions
    ) {
    }

    private record ConceptPerformanceAccumulator(
            String conceptName,
            int correctAnswers,
            int totalQuestions,
            OffsetDateTime latestCompletedAt,
            String noteId
    ) {
        private ConceptPerformanceAccumulator merge(ConceptPerformanceAccumulator other) {
            OffsetDateTime mergedLatestCompletedAt = latestCompletedAt;
            String mergedNoteId = noteId;
            if (other.latestCompletedAt != null
                    && (mergedLatestCompletedAt == null || other.latestCompletedAt.isAfter(mergedLatestCompletedAt))) {
                mergedLatestCompletedAt = other.latestCompletedAt;
                mergedNoteId = other.noteId;
            }
            return new ConceptPerformanceAccumulator(
                    conceptName,
                    correctAnswers + other.correctAnswers,
                    totalQuestions + other.totalQuestions,
                    mergedLatestCompletedAt,
                    mergedNoteId
            );
        }

        private BigDecimal accuracyPercentage() {
            if (totalQuestions <= 0) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(correctAnswers)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalQuestions), 2, RoundingMode.HALF_UP);
        }

        private DashboardConceptInsightResponse toInsight() {
            return new DashboardConceptInsightResponse(conceptName, accuracyPercentage());
        }
    }

    private record ContinueStudyingNoteMetadata(
            String noteTitle,
            String subject,
            String courseProgram
    ) {
    }

    private static final Set<QuickReviewSessionMode> REVIEWABLE_SESSION_MODES = EnumSet.of(
            QuickReviewSessionMode.QUICK_REVIEW,
            QuickReviewSessionMode.CHALLENGE
    );

    /**
     * Return performance stats grouped by note, sorted by best score DESC.
     * Only QUICK_REVIEW and CHALLENGE sessions are included since those
     * are the two modes that support session review navigation.
     */
    public List<NotePerformanceSummaryResponse> getNotePerformanceSummary(UUID userId, int limit) {
        List<QuickReviewSessionEntity> sessions = quickReviewSessionRepository
                .findByUserIdAndSessionModeInAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId, REVIEWABLE_SESSION_MODES);

        if (sessions.isEmpty()) {
            return List.of();
        }

        record NoteStat(
                UUID noteId,
                BigDecimal bestScore,
                BigDecimal averageScore,
                int attemptCount,
                OffsetDateTime lastAttemptedAt,
                UUID bestSessionId,
                String bestSessionMode
        ) {}

        Map<UUID, List<QuickReviewSessionEntity>> byNote = new LinkedHashMap<>();
        for (QuickReviewSessionEntity s : sessions) {
            byNote.computeIfAbsent(s.getNoteId(), k -> new ArrayList<>()).add(s);
        }

        List<NoteStat> stats = new ArrayList<>();
        for (var entry : byNote.entrySet()) {
            UUID noteId = entry.getKey();
            List<QuickReviewSessionEntity> noteSessions = entry.getValue();

            BigDecimal bestScore = null;
            UUID bestSessionId = null;
            String bestSessionMode = null;
            OffsetDateTime lastAttemptedAt = null;
            BigDecimal totalScore = BigDecimal.ZERO;
            int validScoreCount = 0;

            for (QuickReviewSessionEntity s : noteSessions) {
                if (s.getScorePercentage() != null) {
                    if (bestScore == null || s.getScorePercentage().compareTo(bestScore) > 0) {
                        bestScore = s.getScorePercentage();
                        bestSessionId = s.getId();
                        bestSessionMode = s.getSessionMode().name();
                    }
                    totalScore = totalScore.add(s.getScorePercentage());
                    validScoreCount++;
                }
                if (s.getCompletedAt() != null && (lastAttemptedAt == null || s.getCompletedAt().isAfter(lastAttemptedAt))) {
                    lastAttemptedAt = s.getCompletedAt();
                }
            }

            BigDecimal averageScore = validScoreCount > 0
                    ? totalScore.divide(BigDecimal.valueOf(validScoreCount), 2, RoundingMode.HALF_UP)
                    : null;

            stats.add(new NoteStat(noteId, bestScore, averageScore, noteSessions.size(), lastAttemptedAt, bestSessionId, bestSessionMode));
        }

        stats.sort(Comparator.<NoteStat, BigDecimal>comparing(
                        NoteStat::bestScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(NoteStat::lastAttemptedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        List<NoteStat> topStats = stats.stream().limit(limit).toList();

        Set<UUID> noteIds = new LinkedHashSet<>();
        for (NoteStat stat : topStats) {
            noteIds.add(stat.noteId());
        }
        Map<UUID, String> noteTitlesById = new HashMap<>();
        for (var note : noteRepository.findAllById(noteIds)) {
            noteTitlesById.put(note.getId(), note.getTitle());
        }

        return topStats.stream()
                .map(stat -> new NotePerformanceSummaryResponse(
                        stat.noteId(),
                        noteTitlesById.getOrDefault(stat.noteId(), null),
                        stat.bestScore(),
                        stat.averageScore(),
                        stat.attemptCount(),
                        stat.lastAttemptedAt(),
                        stat.bestSessionId(),
                        stat.bestSessionMode()
                ))
                .toList();
    }
}
