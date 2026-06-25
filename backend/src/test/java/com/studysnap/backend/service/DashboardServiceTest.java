package com.studysnap.backend.service;

import com.studysnap.backend.dto.ContinueStudyingReason;
import com.studysnap.backend.dto.ContinueStudyingResumeState;
import com.studysnap.backend.dto.ContinueStudyingResponse;
import com.studysnap.backend.dto.ContinueStudyingResumeType;
import com.studysnap.backend.dto.DashboardOverviewResponse;
import com.studysnap.backend.dto.MasterySnapshotResponse;
import com.studysnap.backend.dto.TodayFocusResponse;
import com.studysnap.backend.dto.TodayFocusType;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.ProfileType;
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
import com.studysnap.backend.repository.QuickReviewSessionMetadataProjection;
import com.studysnap.backend.repository.QuickReviewSessionSummaryProjection;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.testutil.builders.QuickReviewSessionEntityBuilder;
import com.studysnap.backend.testutil.builders.StudyPackEntityBuilder;
import com.studysnap.backend.testutil.builders.UserActivityEventEntityBuilder;
import com.studysnap.backend.testutil.builders.UserEntityBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Mock
    private ActivityEventRepository activityEventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private UserUsageService userUsageService;
    @Mock
    private BillingUsagePeriodService billingUsagePeriodService;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        FeatureGateService featureGateService = new FeatureGateService(subscriptionService, new StudySnapProperties());
        dashboardService = new DashboardService(
                userRepository,
                studyPackRepository,
                noteRepository,
                quickReviewSessionRepository,
                activityEventRepository,
                subscriptionService,
                featureGateService,
                userUsageService,
                billingUsagePeriodService
        );
        lenient().when(subscriptionService.resolvePlan(any(UUID.class))).thenReturn(PlanType.PRO);
        OffsetDateTime periodStart = OffsetDateTime.now().minusDays(10);
        OffsetDateTime periodEnd = periodStart.plusMonths(1);
        lenient().when(billingUsagePeriodService.resolveUsagePeriod(
                        any(UUID.class),
                        any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.PRO,
                        BillingCycle.MONTHLY,
                        periodStart,
                        periodEnd,
                        periodStart.getYear(),
                        periodStart.getMonthValue()
                ));
        lenient().when(userUsageService.getMonthlyUsage(any(UUID.class), any(OffsetDateTime.class)))
                .thenReturn(UserUsageService.MonthlyUsage.zero(periodStart, periodEnd));
        lenient().when(activityEventRepository.findTopByUserIdAndStudyPackIdAndActivityTypeOrderByCreatedAtDesc(
                        any(UUID.class),
                        any(UUID.class),
                        eq(ActivityType.OPENED_STUDY_PACK)))
                .thenReturn(Optional.empty());
        lenient().when(noteRepository.findByIdAndOwnerUserId(any(UUID.class), any(UUID.class)))
                .thenReturn(Optional.empty());
    }

    @Test
    void getStudyEngagement_returnsFocusedDefaultWhenUserMissing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        var response = dashboardService.getStudyEngagement(userId);

        assertThat(response.engagementMode()).isEqualTo(EngagementMode.FOCUSED);
        assertThat(response.currentStreak()).isZero();
        assertThat(response.longestStreak()).isZero();
        assertThat(response.studyDaysThisWeek()).isZero();
    }

    @Test
    void getStudyEngagement_returnsConsistencyMetricsForConsistencyMode() {
        UUID userId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-05-23T12:00:00Z");
        UserEntity user = UserEntityBuilder.aUser()
                .withId(userId)
                .withEngagementMode(EngagementMode.CONSISTENCY)
                .withCurrentStreak(0)
                .withLongestStreak(4)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(activityEventRepository.findByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                eq(userId),
                any(),
                any(OffsetDateTime.class)
        )).thenReturn(List.of(
                buildActivityEvent(userId, ActivityType.COMPLETED_QUICK_REVIEW, now.minusDays(1)),
                buildActivityEvent(userId, ActivityType.CREATED_STUDY_PACK, now.minusDays(1).minusHours(3)),
                buildActivityEvent(userId, ActivityType.COMPLETED_ADAPTIVE_QUIZ, now.minusDays(2))
        ));

        var response = dashboardService.getStudyEngagement(userId);

        assertThat(response.engagementMode()).isEqualTo(EngagementMode.CONSISTENCY);
        assertThat(response.currentStreak()).isZero();
        assertThat(response.longestStreak()).isEqualTo(4);
        assertThat(response.studyDaysThisWeek()).isEqualTo(2);
    }

    @Test
    void getStudyEngagement_returnsStreakMetricsForStreakMode() {
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntityBuilder.aUser()
                .withId(userId)
                .withEngagementMode(EngagementMode.STREAK)
                .withCurrentStreak(5)
                .withLongestStreak(8)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(activityEventRepository.findByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                eq(userId),
                any(),
                any(OffsetDateTime.class)
        )).thenReturn(List.of());

        var response = dashboardService.getStudyEngagement(userId);

        assertThat(response.engagementMode()).isEqualTo(EngagementMode.STREAK);
        assertThat(response.currentStreak()).isEqualTo(5);
        assertThat(response.longestStreak()).isEqualTo(8);
        assertThat(response.studyDaysThisWeek()).isZero();
    }

    @Test
    void getMasterySnapshot_returnsEmptyWhenNoCompletedSessionsExist() {
        UUID userId = UUID.randomUUID();
        when(quickReviewSessionRepository.findCompletedSessionSummariesByUserIdAndSessionModeOrderByCompletedAtDesc(
                eq(userId),
                eq(QuickReviewSessionMode.QUICK_REVIEW),
                any(Pageable.class)
        )).thenReturn(List.of());

        MasterySnapshotResponse response = dashboardService.getMasterySnapshot(userId);

        assertThat(response.averageRecentScore()).isNull();
        assertThat(response.bestRecentScore()).isNull();
        assertThat(response.studyPacksReviewed()).isZero();
    }

    @Test
    void getMasterySnapshot_computesAverageBestAndDistinctStudyPacksReviewed() {
        UUID userId = UUID.randomUUID();
        UUID firstPackId = UUID.randomUUID();
        UUID secondPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        QuickReviewSessionEntity sessionA = buildCompletedSession(userId, firstPackId, bigDecimal(80), now.minusDays(1));
        QuickReviewSessionEntity sessionB = buildCompletedSession(userId, secondPackId, bigDecimal(100), now.minusHours(8));
        QuickReviewSessionEntity sessionC = buildCompletedSession(userId, firstPackId, bigDecimal(50), now.minusHours(2));

        when(quickReviewSessionRepository.findCompletedSessionSummariesByUserIdAndSessionModeOrderByCompletedAtDesc(
                eq(userId),
                eq(QuickReviewSessionMode.QUICK_REVIEW),
                any(Pageable.class)
        )).thenReturn(toSummaryProjections(List.of(sessionC, sessionB, sessionA)));

        MasterySnapshotResponse response = dashboardService.getMasterySnapshot(userId);

        assertThat(response.averageRecentScore()).isEqualByComparingTo(new BigDecimal("76.67"));
        assertThat(response.bestRecentScore()).isEqualByComparingTo(bigDecimal(100));
        assertThat(response.studyPacksReviewed()).isEqualTo(2);
    }

    @Test
    void getOverview_returnsPerformanceFocusAreasAndWeeklyActivity() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        // Pin to a fixed mid-day instant: sub-hour offsets below cross calendar-day
        // boundaries when run near midnight, making studyDays nondeterministic.
        OffsetDateTime now = OffsetDateTime.parse("2026-05-23T12:00:00Z");
        QuickReviewSessionEntity quickReview = buildCompletedSession(userId, UUID.randomUUID(), bigDecimal(75), now.minusDays(3));
        QuickReviewSessionEntity challengeOne = buildCompletedChallengeSession(
                userId,
                UUID.randomUUID(),
                noteId,
                bigDecimal(50),
                now.minusDays(2),
                List.of(
                        conceptBreakdownEntry("Algebra", 1, 4),
                        conceptBreakdownEntry("Geometry", 4, 5)
                )
        );
        QuickReviewSessionEntity challengeTwo = buildCompletedChallengeSession(
                userId,
                UUID.randomUUID(),
                noteId,
                bigDecimal(80),
                now.minusDays(1),
                List.of(
                        conceptBreakdownEntry("Algebra", 2, 4),
                        conceptBreakdownEntry("Physics", 4, 4)
                )
        );

        when(quickReviewSessionRepository.findCompletedSessionSummariesByUserIdAndSessionModeOrderByCompletedAtDesc(
                userId,
                QuickReviewSessionMode.QUICK_REVIEW,
                Pageable.unpaged()
        )).thenReturn(toSummaryProjections(List.of(quickReview)));
        when(quickReviewSessionRepository.findCompletedSessionMetadataByUserIdAndSessionModeOrderByCompletedAtDesc(
                userId,
                QuickReviewSessionMode.CHALLENGE
        )).thenReturn(toMetadataProjections(List.of(challengeTwo, challengeOne)));
        when(studyPackRepository.countByOwnerUserId(userId)).thenReturn(3L);
        when(activityEventRepository.findByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                eq(userId),
                any(),
                any(OffsetDateTime.class)
        )).thenReturn(List.of(
                buildActivityEvent(userId, ActivityType.CREATED_STUDY_PACK, now.minusDays(2)),
                buildActivityEvent(userId, ActivityType.COMPLETED_QUICK_REVIEW, now.minusDays(2).minusHours(2)),
                buildActivityEvent(userId, ActivityType.COMPLETED_CHALLENGE_QUIZ, now.minusDays(1)),
                buildActivityEvent(userId, ActivityType.STARTED_ADAPTIVE_PRACTICE, now.minusDays(1).minusHours(1))
        ));

        DashboardOverviewResponse response = dashboardService.getOverview(userId);

        assertThat(response.performanceSummary().averageQuizScore()).isEqualByComparingTo("68.33");
        assertThat(response.performanceSummary().totalQuizzesTaken()).isEqualTo(3);
        assertThat(response.performanceSummary().studyPacksCreated()).isEqualTo(3);
        assertThat(response.performanceSummary().strongestConcept().conceptName()).isEqualTo("Physics");
        assertThat(response.performanceSummary().weakestConcept().conceptName()).isEqualTo("Algebra");
        assertThat(response.focusAreas().concepts()).hasSize(3);
        assertThat(response.focusAreas().concepts().getFirst().conceptName()).isEqualTo("Algebra");
        assertThat(response.focusAreas().practiceNoteId()).isEqualTo(noteId.toString());
        assertThat(response.focusAreas().adaptivePracticeAvailable()).isTrue();
        assertThat(response.weeklyActivity().studyPacksCreated()).isEqualTo(1);
        assertThat(response.weeklyActivity().quizzesTaken()).isEqualTo(2);
        assertThat(response.weeklyActivity().adaptiveSessions()).isEqualTo(1);
        assertThat(response.weeklyActivity().studyDays()).isEqualTo(2);
    }

    @Test
    void getOverview_marksAdaptivePracticeAvailableForFreeUsersWithQuota() {
        UUID userId = UUID.randomUUID();
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(quickReviewSessionRepository.findCompletedSessionSummariesByUserIdAndSessionModeOrderByCompletedAtDesc(
                userId,
                QuickReviewSessionMode.QUICK_REVIEW,
                Pageable.unpaged()
        )).thenReturn(List.of());
        when(quickReviewSessionRepository.findCompletedSessionMetadataByUserIdAndSessionModeOrderByCompletedAtDesc(
                userId,
                QuickReviewSessionMode.CHALLENGE
        )).thenReturn(List.of());
        when(studyPackRepository.countByOwnerUserId(userId)).thenReturn(0L);
        when(activityEventRepository.findByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                eq(userId),
                any(),
                any(OffsetDateTime.class)
        )).thenReturn(List.of());

        DashboardOverviewResponse response = dashboardService.getOverview(userId);

        assertThat(response.focusAreas().adaptivePracticeAvailable()).isTrue();
    }

    @Test
    void getContinueStudyingRecommendation_prefersInProgressSession() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        StudyPackEntity studyPack = buildStudyPack(userId, studyPackId, "Biology");
        QuickReviewSessionEntity inProgress = buildInProgressSession(
                userId,
                studyPackId,
                1,
                5,
                QuickReviewRound.RETRY,
                Map.of("retryQuestionIndexes", List.of(1, 3, 4)),
                now.minusMinutes(4)
        );

        stubLatestQuickReviewInProgressSession(userId, inProgress);
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isEqualTo(ContinueStudyingReason.RESUME_REVIEW);
        assertThat(response.studyPackId()).isEqualTo(studyPackId.toString());
        assertThat(response.currentRound()).isEqualTo(QuickReviewRound.RETRY);
        assertThat(response.remainingQuestions()).isEqualTo(2);
        assertThat(response.resumeState()).isEqualTo(ContinueStudyingResumeState.RETRY_IN_PROGRESS);
        assertThat(response.resumeType()).isEqualTo(ContinueStudyingResumeType.QUICK_REVIEW);
    }

    @Test
    void getContinueStudyingRecommendation_includesNoteMetadataAndChallengeResumeType() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        StudyPackEntity studyPack = buildStudyPack(userId, studyPackId, "Generated Challenge Pack");
        studyPack.setNoteId(noteId);
        studyPack.setSubject("General Engineering");
        QuickReviewSessionEntity inProgress = buildInProgressSession(
                userId,
                studyPackId,
                2,
                10,
                QuickReviewRound.INITIAL,
                Map.of(),
                now.minusMinutes(3)
        );
        inProgress.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        inProgress.setRetryCount(0);

        stubLatestQuickReviewInProgressSession(userId, inProgress);
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(
                buildNote(userId, noteId, "Statics Midterm Review", "Engineering Mechanics", "Civil Engineering")
        ));

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isEqualTo(ContinueStudyingReason.RESUME_REVIEW);
        assertThat(response.resumeType()).isEqualTo(ContinueStudyingResumeType.CHALLENGE);
        assertThat(response.noteTitle()).isEqualTo("Statics Midterm Review");
        assertThat(response.subject()).isEqualTo("Engineering Mechanics");
        assertThat(response.courseProgram()).isEqualTo("Civil Engineering");
        assertThat(response.currentQuestionIndex()).isEqualTo(2);
        assertThat(response.totalQuestions()).isEqualTo(10);
    }

    @Test
    void getContinueStudyingRecommendation_prefersNewestValidInProgressSessionAcrossModes() {
        UUID userId = UUID.randomUUID();
        UUID missingPackId = UUID.randomUUID();
        UUID adaptivePackId = UUID.randomUUID();
        UUID quickReviewPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        QuickReviewSessionEntity newestBrokenChallenge = buildInProgressSession(
                userId,
                missingPackId,
                4,
                12,
                QuickReviewRound.INITIAL,
                Map.of(),
                now.minusMinutes(2)
        );
        newestBrokenChallenge.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        newestBrokenChallenge.setRetryCount(0);

        QuickReviewSessionEntity newestValidAdaptive = buildInProgressSession(
                userId,
                adaptivePackId,
                1,
                8,
                QuickReviewRound.INITIAL,
                Map.of(),
                now.minusMinutes(3)
        );
        newestValidAdaptive.setSessionMode(QuickReviewSessionMode.ADAPTIVE);
        newestValidAdaptive.setRetryCount(0);

        QuickReviewSessionEntity olderQuickReview = buildInProgressSession(
                userId,
                quickReviewPackId,
                0,
                5,
                QuickReviewRound.INITIAL,
                Map.of(),
                now.minusMinutes(5)
        );
        olderQuickReview.setRetryCount(0);

        StudyPackEntity adaptivePack = buildStudyPack(userId, adaptivePackId, "Adaptive Physics Review");
        adaptivePack.setSubject("Physics");

        when(quickReviewSessionRepository.findTopByUserIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionMode.QUICK_REVIEW,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.of(olderQuickReview));
        when(quickReviewSessionRepository.findTopByUserIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionMode.CHALLENGE,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.of(newestBrokenChallenge));
        when(quickReviewSessionRepository.findTopByUserIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionMode.ADAPTIVE,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.of(newestValidAdaptive));
        when(studyPackRepository.findByIdAndOwnerUserId(missingPackId, userId)).thenReturn(Optional.empty());
        when(studyPackRepository.findByIdAndOwnerUserId(adaptivePackId, userId)).thenReturn(Optional.of(adaptivePack));

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isEqualTo(ContinueStudyingReason.RESUME_REVIEW);
        assertThat(response.resumeType()).isEqualTo(ContinueStudyingResumeType.ADAPTIVE);
        assertThat(response.studyPackId()).isEqualTo(adaptivePackId.toString());
        assertThat(response.currentQuestionIndex()).isEqualTo(1);
        assertThat(response.totalQuestions()).isEqualTo(8);
    }

    @Test
    void getContinueStudyingRecommendation_fallsBackToStudyPackMetadataWhenNoteMissing() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        StudyPackEntity studyPack = buildStudyPack(userId, studyPackId, "Cell Transport Reviewer");
        studyPack.setNoteId(noteId);
        studyPack.setSubject("Biology - Cell Transport");
        QuickReviewSessionEntity inProgress = buildInProgressSession(
                userId,
                studyPackId,
                0,
                6,
                QuickReviewRound.INITIAL,
                Map.of(),
                now.minusMinutes(2)
        );
        inProgress.setRetryCount(0);

        stubLatestInProgressSession(userId, inProgress);
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.empty());

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.noteTitle()).isEqualTo("Cell Transport Reviewer");
        assertThat(response.subject()).isEqualTo("Biology - Cell Transport");
        assertThat(response.courseProgram()).isNull();
    }

    @Test
    void getContinueStudyingRecommendation_usesStudyPackMetadataWhenNoteFieldsAreBlank() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        StudyPackEntity studyPack = buildStudyPack(userId, studyPackId, "Genetics Finals");
        studyPack.setNoteId(noteId);
        studyPack.setSubject("Biology - Genetics");
        QuickReviewSessionEntity inProgress = buildInProgressSession(
                userId,
                studyPackId,
                3,
                10,
                QuickReviewRound.INITIAL,
                Map.of(),
                now.minusMinutes(1)
        );
        inProgress.setRetryCount(0);

        stubLatestInProgressSession(userId, inProgress);
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(
                buildNote(userId, noteId, "   ", "   ", "   ")
        ));

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.noteTitle()).isEqualTo("Genetics Finals");
        assertThat(response.subject()).isEqualTo("Biology - Genetics");
        assertThat(response.courseProgram()).isNull();
    }

    @Test
    void getContinueStudyingRecommendation_setsRetryTransitionResumeStateAfterInitialPassEnds() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId, studyPackId, "Biology");
        QuickReviewSessionEntity inProgress = buildInProgressSession(
                userId,
                studyPackId,
                5,
                5,
                QuickReviewRound.INITIAL,
                Map.of("retryQuestionIndexes", List.of(0, 3)),
                OffsetDateTime.now().minusMinutes(5)
        );

        stubLatestQuickReviewInProgressSession(userId, inProgress);
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isEqualTo(ContinueStudyingReason.RESUME_REVIEW);
        assertThat(response.resumeState()).isEqualTo(ContinueStudyingResumeState.RETRY_TRANSITION);
        assertThat(response.currentRound()).isEqualTo(QuickReviewRound.INITIAL);
    }

    @Test
    void getContinueStudyingRecommendation_setsQuestionInProgressResumeStateDuringInitialFlow() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId, studyPackId, "Biology");
        QuickReviewSessionEntity inProgress = buildInProgressSession(
                userId,
                studyPackId,
                2,
                5,
                QuickReviewRound.INITIAL,
                Map.of(),
                OffsetDateTime.now().minusMinutes(5)
        );
        inProgress.setRetryCount(0);

        stubLatestQuickReviewInProgressSession(userId, inProgress);
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isEqualTo(ContinueStudyingReason.RESUME_REVIEW);
        assertThat(response.resumeState()).isEqualTo(ContinueStudyingResumeState.QUESTION_IN_PROGRESS);
    }

    @Test
    void getContinueStudyingRecommendation_selectsWeakestRecentlyReviewedPack() {
        UUID userId = UUID.randomUUID();
        UUID weakPackId = UUID.randomUUID();
        UUID strongerPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        StudyPackEntity weakPack = buildStudyPack(userId, weakPackId, "Chemistry");

        stubNoInProgressSessions(userId);
        stubRecentQuickReviewSessions(userId, List.of(
                buildCompletedSession(userId, strongerPackId, bigDecimal(75), now.minusMinutes(10)),
                buildCompletedSession(userId, weakPackId, bigDecimal(50), now.minusMinutes(20))
        ));
        when(studyPackRepository.findByIdAndOwnerUserId(weakPackId, userId)).thenReturn(Optional.of(weakPack));

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isEqualTo(ContinueStudyingReason.LOW_SCORE_RECENT);
        assertThat(response.studyPackId()).isEqualTo(weakPackId.toString());
        assertThat(response.lastScorePercentage()).isEqualByComparingTo(bigDecimal(50));
    }

    @Test
    void getContinueStudyingRecommendation_usesMostRecentWhenWeakScoresTie() {
        UUID userId = UUID.randomUUID();
        UUID mostRecentPackId = UUID.randomUUID();
        UUID olderPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        StudyPackEntity mostRecentPack = buildStudyPack(userId, mostRecentPackId, "World History");

        stubNoInProgressSessions(userId);
        stubRecentQuickReviewSessions(userId, List.of(
                buildCompletedSession(userId, mostRecentPackId, bigDecimal(60), now.minusMinutes(5)),
                buildCompletedSession(userId, olderPackId, bigDecimal(60), now.minusMinutes(30))
        ));
        when(studyPackRepository.findByIdAndOwnerUserId(mostRecentPackId, userId)).thenReturn(Optional.of(mostRecentPack));

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isEqualTo(ContinueStudyingReason.LOW_SCORE_RECENT);
        assertThat(response.studyPackId()).isEqualTo(mostRecentPackId.toString());
        assertThat(response.lastScorePercentage()).isEqualByComparingTo(bigDecimal(60));
    }

    @Test
    void getContinueStudyingRecommendation_ignoresPerfectPackWhenWeakerExists() {
        UUID userId = UUID.randomUUID();
        UUID perfectPackId = UUID.randomUUID();
        UUID weakerPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        StudyPackEntity weakerPack = buildStudyPack(userId, weakerPackId, "Physics");

        stubNoInProgressSessions(userId);
        stubRecentQuickReviewSessions(userId, List.of(
                buildCompletedSession(userId, perfectPackId, bigDecimal(100), now.minusMinutes(10)),
                buildCompletedSession(userId, weakerPackId, bigDecimal(60), now.minusMinutes(20))
        ));
        when(studyPackRepository.findByIdAndOwnerUserId(weakerPackId, userId)).thenReturn(Optional.of(weakerPack));

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isEqualTo(ContinueStudyingReason.LOW_SCORE_RECENT);
        assertThat(response.studyPackId()).isEqualTo(weakerPackId.toString());
        assertThat(response.lastScorePercentage()).isEqualByComparingTo(bigDecimal(60));
    }

    @Test
    void getContinueStudyingRecommendation_suggestsChallengeForEligibleStudentUsingMostRecentlyOpenedReadyPack() {
        UUID userId = UUID.randomUUID();
        UUID newestPackId = UUID.randomUUID();
        UUID olderPackId = UUID.randomUUID();
        UUID newestNoteId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        StudyPackEntity newestPack = buildStudyPack(userId, newestPackId, "Biology Challenge");
        newestPack.setNoteId(newestNoteId);
        StudyPackEntity olderPack = buildStudyPack(userId, olderPackId, "Older Chemistry Challenge");
        olderPack.setNoteId(UUID.randomUUID());
        UserActivityEventEntity newestOpened = buildOpenedEvent(userId, newestPackId, now.minusMinutes(2));
        UserActivityEventEntity olderOpened = buildOpenedEvent(userId, olderPackId, now.minusMinutes(20));

        stubChallengeSuggestionCandidate(userId, ProfileType.STUDENT);
        when(activityEventRepository.findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
                eq(userId),
                eq(ActivityType.OPENED_STUDY_PACK),
                any(Pageable.class)
        )).thenReturn(List.of(newestOpened, olderOpened));
        when(studyPackRepository.findByIdAndOwnerUserId(newestPackId, userId)).thenReturn(Optional.of(newestPack));

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isEqualTo(ContinueStudyingReason.SUGGESTED_CHALLENGE);
        assertThat(response.resumeType()).isEqualTo(ContinueStudyingResumeType.CHALLENGE);
        assertThat(response.studyPackId()).isEqualTo(newestPackId.toString());
        assertThat(response.noteId()).isEqualTo(newestNoteId.toString());
    }

    @Test
    void getContinueStudyingRecommendation_usesMostRecentlyCreatedReadyPackWhenNoneWasOpened() {
        UUID userId = UUID.randomUUID();
        UUID newestPackId = UUID.randomUUID();
        UUID olderPackId = UUID.randomUUID();
        StudyPackEntity newestPack = buildStudyPack(userId, newestPackId, "Newest Ready Pack");
        newestPack.setNoteId(UUID.randomUUID());
        StudyPackEntity olderPack = buildStudyPack(userId, olderPackId, "Older Ready Pack");
        olderPack.setNoteId(UUID.randomUUID());

        stubChallengeSuggestionCandidate(userId, ProfileType.BOARD_EXAM);
        when(activityEventRepository.findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
                eq(userId),
                eq(ActivityType.OPENED_STUDY_PACK),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(studyPackRepository.findByOwnerUserIdOrderByCreatedAtDescIdDesc(eq(userId), any(Pageable.class)))
                .thenReturn(List.of(newestPack, olderPack));

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isEqualTo(ContinueStudyingReason.SUGGESTED_CHALLENGE);
        assertThat(response.studyPackId()).isEqualTo(newestPackId.toString());
    }

    @Test
    void getContinueStudyingRecommendation_fallsBackWhenChallengeWasAlreadyStarted() {
        UUID userId = UUID.randomUUID();
        UUID openedPackId = UUID.randomUUID();
        StudyPackEntity openedPack = buildStudyPack(userId, openedPackId, "Geometry");
        openedPack.setNoteId(UUID.randomUUID());
        UserActivityEventEntity openedEvent = buildOpenedEvent(userId, openedPackId, OffsetDateTime.now());

        stubChallengeSuggestionCandidate(userId, ProfileType.STUDENT);
        when(quickReviewSessionRepository.existsByUserIdAndSessionMode(userId, QuickReviewSessionMode.CHALLENGE))
                .thenReturn(true);
        stubRecentlyOpenedFallback(userId, openedPack, openedEvent);

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isEqualTo(ContinueStudyingReason.RECENTLY_OPENED);
    }

    @Test
    void getContinueStudyingRecommendation_fallsBackForTeacherAndProfessionalProfiles() {
        for (ProfileType profileType : List.of(ProfileType.TEACHER, ProfileType.PROFESSIONAL)) {
            UUID userId = UUID.randomUUID();
            UUID openedPackId = UUID.randomUUID();
            StudyPackEntity openedPack = buildStudyPack(userId, openedPackId, profileType + " Pack");
            openedPack.setNoteId(UUID.randomUUID());
            UserActivityEventEntity openedEvent = buildOpenedEvent(userId, openedPackId, OffsetDateTime.now());

            stubChallengeSuggestionCandidate(userId, profileType);
            stubRecentlyOpenedFallback(userId, openedPack, openedEvent);

            ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

            assertThat(response.reason()).isEqualTo(ContinueStudyingReason.RECENTLY_OPENED);
        }
    }

    @Test
    void getContinueStudyingRecommendation_fallsBackWhenChallengeMonthlyLimitIsReached() {
        UUID userId = UUID.randomUUID();
        UUID openedPackId = UUID.randomUUID();
        StudyPackEntity openedPack = buildStudyPack(userId, openedPackId, "Quota Fallback");
        openedPack.setNoteId(UUID.randomUUID());
        UserActivityEventEntity openedEvent = buildOpenedEvent(userId, openedPackId, OffsetDateTime.now());
        OffsetDateTime periodStart = OffsetDateTime.now().minusDays(5);
        OffsetDateTime periodEnd = periodStart.plusMonths(1);

        stubChallengeSuggestionCandidate(userId, ProfileType.STUDENT);
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new UserUsageService.MonthlyUsage(
                        periodStart,
                        periodEnd,
                        0,
                        50,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0
                ));
        stubRecentlyOpenedFallback(userId, openedPack, openedEvent);

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isEqualTo(ContinueStudyingReason.RECENTLY_OPENED);
    }

    @Test
    void getContinueStudyingRecommendation_fallsBackToRecentlyOpened() {
        UUID userId = UUID.randomUUID();
        UUID openedPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        StudyPackEntity openedPack = buildStudyPack(userId, openedPackId, "Geometry");
        UserActivityEventEntity openedEvent = buildOpenedEvent(userId, openedPackId, now.minusMinutes(8));

        stubNoInProgressSessions(userId);
        stubRecentQuickReviewSessions(userId, List.of());
        when(activityEventRepository.findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
                eq(userId),
                eq(ActivityType.OPENED_STUDY_PACK),
                any(Pageable.class)
        )).thenReturn(List.of(openedEvent));
        when(studyPackRepository.findByIdAndOwnerUserId(openedPackId, userId)).thenReturn(Optional.of(openedPack));
        stubRecentQuickReviewSessionsForPack(userId, openedPackId, List.of());

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isEqualTo(ContinueStudyingReason.RECENTLY_OPENED);
        assertThat(response.studyPackId()).isEqualTo(openedPackId.toString());
    }

    @Test
    void getContinueStudyingRecommendation_fallsBackToRecentlyCreated() {
        UUID userId = UUID.randomUUID();
        UUID createdPackId = UUID.randomUUID();
        StudyPackEntity createdPack = buildStudyPack(userId, createdPackId, "English Literature");

        stubNoInProgressSessions(userId);
        stubRecentQuickReviewSessions(userId, List.of());
        when(activityEventRepository.findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
                eq(userId),
                eq(ActivityType.OPENED_STUDY_PACK),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(studyPackRepository.findTopByOwnerUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(createdPack));

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isEqualTo(ContinueStudyingReason.RECENTLY_CREATED);
        assertThat(response.studyPackId()).isEqualTo(createdPackId.toString());
    }

    @Test
    void getContinueStudyingRecommendation_returnsEmptyWhenNoStudyPacksExist() {
        UUID userId = UUID.randomUUID();

        stubNoInProgressSessions(userId);
        stubRecentQuickReviewSessions(userId, List.of());
        when(activityEventRepository.findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
                eq(userId),
                eq(ActivityType.OPENED_STUDY_PACK),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(studyPackRepository.findTopByOwnerUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isNull();
        assertThat(response.studyPackId()).isNull();
        assertThat(response.noteTitle()).isNull();
    }

    @Test
    void getContinueStudyingRecommendation_usesLatestCompletedSessionPerPackNotHistoricalBest() {
        UUID userId = UUID.randomUUID();
        UUID packWithOlderPerfectNewerWeakId = UUID.randomUUID();
        UUID otherPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        StudyPackEntity selectedPack = buildStudyPack(userId, packWithOlderPerfectNewerWeakId, "Programming");

        stubNoInProgressSessions(userId);
        stubRecentQuickReviewSessions(userId, List.of(
                buildCompletedSession(userId, packWithOlderPerfectNewerWeakId, bigDecimal(60), now.minusMinutes(5)),
                buildCompletedSession(userId, otherPackId, bigDecimal(70), now.minusMinutes(10)),
                buildCompletedSession(userId, packWithOlderPerfectNewerWeakId, bigDecimal(100), now.minusDays(1))
        ));
        when(studyPackRepository.findByIdAndOwnerUserId(packWithOlderPerfectNewerWeakId, userId))
                .thenReturn(Optional.of(selectedPack));

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isEqualTo(ContinueStudyingReason.LOW_SCORE_RECENT);
        assertThat(response.studyPackId()).isEqualTo(packWithOlderPerfectNewerWeakId.toString());
        assertThat(response.lastScorePercentage()).isEqualByComparingTo(bigDecimal(60));
    }

    @Test
    void getTodayFocus_prefersResumeReviewWhenQuestionInProgress() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId, studyPackId, "Biology");
        QuickReviewSessionEntity inProgress = buildInProgressSession(
                userId,
                studyPackId,
                2,
                5,
                QuickReviewRound.INITIAL,
                Map.of(),
                OffsetDateTime.now().minusMinutes(4)
        );
        inProgress.setRetryCount(0);

        stubLatestQuickReviewInProgressSession(userId, inProgress);
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));

        TodayFocusResponse response = dashboardService.getTodayFocus(userId);

        assertThat(response.type()).isEqualTo(TodayFocusType.RESUME_REVIEW);
        assertThat(response.studyPackId()).isEqualTo(studyPackId.toString());
        assertThat(response.actionLabel()).isEqualTo("Resume Review");
    }

    @Test
    void getTodayFocus_usesRetryReviewWhenRetryRoundIsInProgress() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId, studyPackId, "Chemistry");
        QuickReviewSessionEntity inProgress = buildInProgressSession(
                userId,
                studyPackId,
                1,
                5,
                QuickReviewRound.RETRY,
                Map.of("retryQuestionIndexes", List.of(0, 2, 4)),
                OffsetDateTime.now().minusMinutes(5)
        );

        stubLatestQuickReviewInProgressSession(userId, inProgress);
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));

        TodayFocusResponse response = dashboardService.getTodayFocus(userId);

        assertThat(response.type()).isEqualTo(TodayFocusType.RETRY_REVIEW);
        assertThat(response.studyPackId()).isEqualTo(studyPackId.toString());
        assertThat(response.actionLabel()).isEqualTo("Retry Questions");
    }

    @Test
    void getTodayFocus_recommendsWeakConceptPracticeWhenLatestCompletedHasWeakConcepts() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        StudyPackEntity studyPack = buildStudyPack(userId, studyPackId, "World History");
        QuickReviewSessionEntity latestCompleted = buildCompletedSession(
                userId,
                studyPackId,
                bigDecimal(60),
                now.minusMinutes(10)
        );
        latestCompleted.setSessionMetadata(Map.of("weakConcepts", List.of("Alliances", "Militarism")));

        stubNoQuickReviewInProgressSession(userId);
        stubRecentQuickReviewSessions(userId, List.of(latestCompleted));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));

        TodayFocusResponse response = dashboardService.getTodayFocus(userId);

        assertThat(response.type()).isEqualTo(TodayFocusType.PRACTICE_WEAK_CONCEPT);
        assertThat(response.studyPackId()).isEqualTo(studyPackId.toString());
        assertThat(response.actionLabel()).isEqualTo("Practice Weak Areas");
    }

    @Test
    void getTodayFocus_skipsWeakConceptPracticeWhenNoWeakConceptsExist() {
        UUID userId = UUID.randomUUID();
        UUID reviewedPackId = UUID.randomUUID();
        StudyPackEntity reviewedPack = buildStudyPack(userId, reviewedPackId, "Reviewed Pack");
        QuickReviewSessionEntity reviewedSession = buildCompletedSession(
                userId,
                reviewedPackId,
                bigDecimal(70),
                OffsetDateTime.now().minusMinutes(2)
        );
        reviewedSession.setSessionMetadata(Map.of("weakConcepts", List.of("  ", "")));

        stubNoQuickReviewInProgressSession(userId);
        stubRecentQuickReviewSessions(userId, List.of(reviewedSession), List.of(reviewedSession));
        when(studyPackRepository.findByIdAndOwnerUserId(reviewedPackId, userId)).thenReturn(Optional.of(reviewedPack));
        when(activityEventRepository.findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
                eq(userId),
                eq(ActivityType.OPENED_STUDY_PACK),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(studyPackRepository.findTopByOwnerUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());

        TodayFocusResponse response = dashboardService.getTodayFocus(userId);

        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.studyPackId()).isEqualTo(reviewedPackId.toString());
        assertThat(response.actionLabel()).isEqualTo("Start Quick Review");
    }

    @Test
    void getTodayFocus_skipsWeakConceptPracticeWhenReviewedPackIsMissing() {
        UUID userId = UUID.randomUUID();
        UUID reviewedPackId = UUID.randomUUID();
        QuickReviewSessionEntity reviewedSession = buildCompletedSession(
                userId,
                reviewedPackId,
                bigDecimal(65),
                OffsetDateTime.now().minusMinutes(4)
        );
        reviewedSession.setSessionMetadata(Map.of("weakConcepts", List.of("Algebra")));

        stubNoQuickReviewInProgressSession(userId);
        stubRecentQuickReviewSessions(userId, List.of(reviewedSession), List.of(reviewedSession));
        when(studyPackRepository.findByIdAndOwnerUserId(reviewedPackId, userId)).thenReturn(Optional.empty());
        when(activityEventRepository.findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
                eq(userId),
                eq(ActivityType.OPENED_STUDY_PACK),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(studyPackRepository.findTopByOwnerUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());

        TodayFocusResponse response = dashboardService.getTodayFocus(userId);

        assertThat(response.type()).isEqualTo(TodayFocusType.STUDY_SUGGESTION);
        assertThat(response.studyPackId()).isNull();
        assertThat(response.actionLabel()).isEqualTo("Create Study Pack");
    }

    @Test
    void getTodayFocus_surfacesWeakConceptPracticeForFreePlan() {
        UUID userId = UUID.randomUUID();
        UUID reviewedPackId = UUID.randomUUID();
        StudyPackEntity reviewedPack = buildStudyPack(userId, reviewedPackId, "Reviewed Pack");
        QuickReviewSessionEntity reviewedSession = buildCompletedSession(
                userId,
                reviewedPackId,
                bigDecimal(70),
                OffsetDateTime.now().minusMinutes(2)
        );
        reviewedSession.setSessionMetadata(Map.of("weakConcepts", List.of("Alliances", "Militarism")));

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        stubNoQuickReviewInProgressSession(userId);
        stubRecentQuickReviewSessions(userId, List.of(reviewedSession), List.of(reviewedSession));
        when(studyPackRepository.findByIdAndOwnerUserId(reviewedPackId, userId)).thenReturn(Optional.of(reviewedPack));

        TodayFocusResponse response = dashboardService.getTodayFocus(userId);

        assertThat(response.type()).isEqualTo(TodayFocusType.PRACTICE_WEAK_CONCEPT);
        assertThat(response.studyPackId()).isEqualTo(reviewedPackId.toString());
    }

    @Test
    void getTodayFocus_fallbackUsesLastOpenedPackWhenAvailable() {
        UUID userId = UUID.randomUUID();
        UUID openedPackId = UUID.randomUUID();
        StudyPackEntity openedPack = buildStudyPack(userId, openedPackId, "Opened Pack");
        UserActivityEventEntity openedEvent = buildOpenedEvent(userId, openedPackId, OffsetDateTime.now().minusMinutes(8));

        stubNoQuickReviewInProgressSession(userId);
        stubRecentQuickReviewSessions(userId, List.of());
        when(activityEventRepository.findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
                eq(userId),
                eq(ActivityType.OPENED_STUDY_PACK),
                any(Pageable.class)
        )).thenReturn(List.of(openedEvent));
        when(studyPackRepository.findByIdAndOwnerUserId(openedPackId, userId)).thenReturn(Optional.of(openedPack));
        TodayFocusResponse response = dashboardService.getTodayFocus(userId);

        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.studyPackId()).isEqualTo(openedPackId.toString());
        assertThat(response.actionLabel()).isEqualTo("Start Quick Review");
    }

    @Test
    void getTodayFocus_fallbackUsesRecentlyCreatedWhenNoOpenedPackExists() {
        UUID userId = UUID.randomUUID();
        UUID createdPackId = UUID.randomUUID();
        StudyPackEntity createdPack = buildStudyPack(userId, createdPackId, "Created Pack");

        stubNoQuickReviewInProgressSession(userId);
        stubRecentQuickReviewSessions(userId, List.of());
        when(activityEventRepository.findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
                eq(userId),
                eq(ActivityType.OPENED_STUDY_PACK),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(studyPackRepository.findTopByOwnerUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(createdPack));

        TodayFocusResponse response = dashboardService.getTodayFocus(userId);

        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.studyPackId()).isEqualTo(createdPackId.toString());
        assertThat(response.actionLabel()).isEqualTo("Start Quick Review");
    }

    @Test
    void getTodayFocus_fallbackUsesMostRecentlyReviewedWhenNoOpenedOrCreatedPackExists() {
        UUID userId = UUID.randomUUID();
        UUID reviewedPackId = UUID.randomUUID();
        StudyPackEntity reviewedPack = buildStudyPack(userId, reviewedPackId, "Reviewed Pack");
        QuickReviewSessionEntity reviewedSession = buildCompletedSession(
                userId,
                reviewedPackId,
                bigDecimal(70),
                OffsetDateTime.now().minusMinutes(2)
        );

        stubNoQuickReviewInProgressSession(userId);
        stubRecentQuickReviewSessions(userId, List.of(), List.of(reviewedSession));
        when(activityEventRepository.findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
                eq(userId),
                eq(ActivityType.OPENED_STUDY_PACK),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(studyPackRepository.findTopByOwnerUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());
        when(studyPackRepository.findByIdAndOwnerUserId(reviewedPackId, userId)).thenReturn(Optional.of(reviewedPack));

        TodayFocusResponse response = dashboardService.getTodayFocus(userId);

        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.studyPackId()).isEqualTo(reviewedPackId.toString());
        assertThat(response.actionLabel()).isEqualTo("Start Quick Review");
    }

    @Test
    void getTodayFocus_returnsStudySuggestionWhenNoStudyPacksExistForFallback() {
        UUID userId = UUID.randomUUID();

        stubNoQuickReviewInProgressSession(userId);
        stubRecentQuickReviewSessions(userId, List.of(), List.of());
        when(activityEventRepository.findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
                eq(userId),
                eq(ActivityType.OPENED_STUDY_PACK),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(studyPackRepository.findTopByOwnerUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());

        TodayFocusResponse response = dashboardService.getTodayFocus(userId);

        assertThat(response.type()).isEqualTo(TodayFocusType.STUDY_SUGGESTION);
        assertThat(response.studyPackId()).isNull();
        assertThat(response.actionLabel()).isEqualTo("Create Study Pack");
    }

    private StudyPackEntity buildStudyPack(UUID userId, UUID studyPackId, String title) {
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(2);
        return StudyPackEntityBuilder.aStudyPack()
                .withId(studyPackId)
                .withOwnerUserId(userId)
                .withTitle(title)
                .withSummary("Summary for " + title)
                .withCreatedAt(createdAt)
                .withUpdatedAt(createdAt)
                .build();
    }

    private QuickReviewSessionEntity buildCompletedSession(
            UUID userId,
            UUID studyPackId,
            BigDecimal scorePercentage,
            OffsetDateTime completedAt
    ) {
        return QuickReviewSessionEntityBuilder.aCompletedSession()
                .withUserId(userId)
                .withStudyPackId(studyPackId)
                .withScorePercentage(scorePercentage)
                .withCorrectAnswers(3)
                .withRetryCount(0)
                .withCurrentRound(QuickReviewRound.INITIAL)
                .withCreatedAt(completedAt.minusMinutes(10))
                .withCompletedAt(completedAt)
                .build();
    }

    private QuickReviewSessionEntity buildInProgressSession(
            UUID userId,
            UUID studyPackId,
            int currentQuestionIndex,
            int totalQuestions,
            QuickReviewRound round,
            Map<String, Object> sessionState,
            OffsetDateTime createdAt
    ) {
        return QuickReviewSessionEntityBuilder.anInProgressSession()
                .withUserId(userId)
                .withStudyPackId(studyPackId)
                .withCurrentQuestionIndex(currentQuestionIndex)
                .withCurrentRound(round)
                .withTotalQuestions(totalQuestions)
                .withRetryCount(1)
                .withSessionState(sessionState)
                .withCreatedAt(createdAt)
                .build();
    }

    private QuickReviewSessionEntity buildCompletedChallengeSession(
            UUID userId,
            UUID studyPackId,
            UUID noteId,
            BigDecimal scorePercentage,
            OffsetDateTime completedAt,
            List<Map<String, Object>> conceptBreakdown
    ) {
        QuickReviewSessionEntity session = QuickReviewSessionEntityBuilder.aCompletedSession()
                .withUserId(userId)
                .withStudyPackId(studyPackId)
                .withScorePercentage(scorePercentage)
                .withCurrentRound(QuickReviewRound.INITIAL)
                .withCreatedAt(completedAt.minusMinutes(10))
                .withCompletedAt(completedAt)
                .build();
        session.setNoteId(noteId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setSessionMetadata(Map.of("conceptBreakdown", conceptBreakdown));
        return session;
    }

    private List<QuickReviewSessionSummaryProjection> toSummaryProjections(List<QuickReviewSessionEntity> sessions) {
        return sessions.stream()
                .map(this::toSummaryProjection)
                .toList();
    }

    private QuickReviewSessionSummaryProjection toSummaryProjection(QuickReviewSessionEntity session) {
        return new QuickReviewSessionSummaryProjection(
                session.getId(),
                session.getUserId(),
                session.getStudyPackId(),
                session.getNoteId(),
                session.getSessionMode(),
                session.getStatus(),
                session.getTotalQuestions(),
                session.getCorrectAnswers(),
                session.getScorePercentage(),
                session.getRetryCount(),
                session.getDurationSeconds(),
                session.getCreatedAt(),
                session.getCompletedAt()
        );
    }

    private List<QuickReviewSessionMetadataProjection> toMetadataProjections(List<QuickReviewSessionEntity> sessions) {
        return sessions.stream()
                .map(this::toMetadataProjection)
                .toList();
    }

    private QuickReviewSessionMetadataProjection toMetadataProjection(QuickReviewSessionEntity session) {
        return new QuickReviewSessionMetadataProjection(
                session.getId(),
                session.getUserId(),
                session.getStudyPackId(),
                session.getNoteId(),
                session.getSessionMode(),
                session.getStatus(),
                session.getTotalQuestions(),
                session.getCorrectAnswers(),
                session.getScorePercentage(),
                session.getRetryCount(),
                session.getDurationSeconds(),
                session.getSessionMetadata(),
                session.getCreatedAt(),
                session.getCompletedAt()
        );
    }

    private void stubLatestInProgressSession(UUID userId, QuickReviewSessionEntity session) {
        QuickReviewSessionMode sessionMode = session == null || session.getSessionMode() == null
                ? QuickReviewSessionMode.QUICK_REVIEW
                : session.getSessionMode();
        when(quickReviewSessionRepository.findTopByUserIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionMode.QUICK_REVIEW,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(sessionMode == QuickReviewSessionMode.QUICK_REVIEW && session != null
                ? Optional.of(session)
                : Optional.empty());
        when(quickReviewSessionRepository.findTopByUserIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionMode.CHALLENGE,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(sessionMode == QuickReviewSessionMode.CHALLENGE
                ? Optional.of(session)
                : Optional.empty());
        when(quickReviewSessionRepository.findTopByUserIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionMode.ADAPTIVE,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(sessionMode == QuickReviewSessionMode.ADAPTIVE
                ? Optional.of(session)
                : Optional.empty());
    }

    private void stubNoInProgressSessions(UUID userId) {
        stubLatestInProgressSession(userId, null);
    }

    private void stubLatestQuickReviewInProgressSession(UUID userId, QuickReviewSessionEntity session) {
        when(quickReviewSessionRepository.findTopByUserIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionMode.QUICK_REVIEW,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(session == null ? Optional.empty() : Optional.of(session));
    }

    private void stubNoQuickReviewInProgressSession(UUID userId) {
        stubLatestQuickReviewInProgressSession(userId, null);
    }

    private void stubRecentQuickReviewSessions(UUID userId, List<QuickReviewSessionEntity> sessions) {
        lenient().when(quickReviewSessionRepository.findCompletedSessionSummariesByUserIdAndSessionModeOrderByCompletedAtDesc(
                eq(userId),
                eq(QuickReviewSessionMode.QUICK_REVIEW),
                any(Pageable.class)
        )).thenReturn(toSummaryProjections(sessions));
        lenient().when(quickReviewSessionRepository.findCompletedSessionMetadataByUserIdAndSessionModeOrderByCompletedAtDesc(
                eq(userId),
                eq(QuickReviewSessionMode.QUICK_REVIEW),
                any(Pageable.class)
        )).thenReturn(toMetadataProjections(sessions));
    }

    private void stubRecentQuickReviewSessions(
            UUID userId,
            List<QuickReviewSessionEntity> firstCall,
            List<QuickReviewSessionEntity> secondCall
    ) {
        lenient().when(quickReviewSessionRepository.findCompletedSessionSummariesByUserIdAndSessionModeOrderByCompletedAtDesc(
                eq(userId),
                eq(QuickReviewSessionMode.QUICK_REVIEW),
                any(Pageable.class)
        )).thenReturn(toSummaryProjections(secondCall));
        lenient().when(quickReviewSessionRepository.findCompletedSessionMetadataByUserIdAndSessionModeOrderByCompletedAtDesc(
                eq(userId),
                eq(QuickReviewSessionMode.QUICK_REVIEW),
                any(Pageable.class)
        )).thenReturn(toMetadataProjections(firstCall));
    }

    private void stubRecentQuickReviewSessionsForPack(
            UUID userId,
            UUID studyPackId,
            List<QuickReviewSessionEntity> sessions
    ) {
        lenient().when(quickReviewSessionRepository.findCompletedSessionSummariesByUserIdAndStudyPackIdAndSessionModeOrderByCompletedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.QUICK_REVIEW),
                any(Pageable.class)
        )).thenReturn(toSummaryProjections(sessions));
    }

    private void stubChallengeSuggestionCandidate(UUID userId, ProfileType profileType) {
        UserEntity user = UserEntityBuilder.aUser().withId(userId).build();
        user.setProfileType(profileType);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        stubNoInProgressSessions(userId);
        stubRecentQuickReviewSessions(userId, List.of());
        lenient().when(quickReviewSessionRepository.existsByUserIdAndSessionMode(userId, QuickReviewSessionMode.CHALLENGE))
                .thenReturn(false);
    }

    private void stubRecentlyOpenedFallback(
            UUID userId,
            StudyPackEntity studyPack,
            UserActivityEventEntity openedEvent
    ) {
        when(activityEventRepository.findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
                eq(userId),
                eq(ActivityType.OPENED_STUDY_PACK),
                any(Pageable.class)
        )).thenReturn(List.of(openedEvent));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPack.getId(), userId)).thenReturn(Optional.of(studyPack));
        stubRecentQuickReviewSessionsForPack(userId, studyPack.getId(), List.of());
    }

    private NoteEntity buildNote(UUID userId, UUID noteId, String title, String subject, String courseProgram) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setOwnerUserId(userId);
        note.setTitle(title);
        note.setSubject(subject);
        note.setCourseProgram(courseProgram);
        note.setContent("Note content");
        return note;
    }

    private UserActivityEventEntity buildOpenedEvent(UUID userId, UUID studyPackId, OffsetDateTime createdAt) {
        return UserActivityEventEntityBuilder.anActivityEvent()
                .withUserId(userId)
                .withStudyPackId(studyPackId)
                .withActivityType(ActivityType.OPENED_STUDY_PACK)
                .withCreatedAt(createdAt)
                .build();
    }

    private UserActivityEventEntity buildActivityEvent(UUID userId, ActivityType activityType, OffsetDateTime createdAt) {
        return UserActivityEventEntityBuilder.anActivityEvent()
                .withUserId(userId)
                .withStudyPackId(UUID.randomUUID())
                .withActivityType(activityType)
                .withCreatedAt(createdAt)
                .build();
    }

    private BigDecimal bigDecimal(int value) {
        return BigDecimal.valueOf(value);
    }

    private Map<String, Object> conceptBreakdownEntry(String concept, int correctAnswers, int totalQuestions) {
        return Map.of(
                "concept", concept,
                "correctAnswers", correctAnswers,
                "totalQuestions", totalQuestions
        );
    }
}
