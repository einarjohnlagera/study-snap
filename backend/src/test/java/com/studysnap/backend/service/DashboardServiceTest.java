package com.studysnap.backend.service;

import com.studysnap.backend.dto.ContinueStudyingReason;
import com.studysnap.backend.dto.ContinueStudyingResumeState;
import com.studysnap.backend.dto.ContinueStudyingResponse;
import com.studysnap.backend.dto.TodayFocusResponse;
import com.studysnap.backend.dto.TodayFocusType;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserActivityEventEntity;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.testutil.builders.QuickReviewSessionEntityBuilder;
import com.studysnap.backend.testutil.builders.StudyPackEntityBuilder;
import com.studysnap.backend.testutil.builders.UserActivityEventEntityBuilder;
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
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Mock
    private ActivityEventRepository activityEventRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                studyPackRepository,
                quickReviewSessionRepository,
                activityEventRepository
        );
        lenient().when(activityEventRepository.findTopByUserIdAndStudyPackIdAndActivityTypeOrderByCreatedAtDesc(
                        any(UUID.class),
                        any(UUID.class),
                        eq(ActivityType.OPENED_STUDY_PACK)))
                .thenReturn(Optional.empty());
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

        when(quickReviewSessionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.of(inProgress));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isEqualTo(ContinueStudyingReason.RESUME_REVIEW);
        assertThat(response.studyPackId()).isEqualTo(studyPackId.toString());
        assertThat(response.currentRound()).isEqualTo(QuickReviewRound.RETRY);
        assertThat(response.remainingQuestions()).isEqualTo(2);
        assertThat(response.resumeState()).isEqualTo(ContinueStudyingResumeState.RETRY_IN_PROGRESS);
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

        when(quickReviewSessionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.of(inProgress));
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

        when(quickReviewSessionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.of(inProgress));
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

        when(quickReviewSessionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(List.of(
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

        when(quickReviewSessionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(List.of(
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

        when(quickReviewSessionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(List.of(
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
    void getContinueStudyingRecommendation_fallsBackToRecentlyOpened() {
        UUID userId = UUID.randomUUID();
        UUID openedPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        StudyPackEntity openedPack = buildStudyPack(userId, openedPackId, "Geometry");
        UserActivityEventEntity openedEvent = buildOpenedEvent(userId, openedPackId, now.minusMinutes(8));

        when(quickReviewSessionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(activityEventRepository.findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
                eq(userId),
                eq(ActivityType.OPENED_STUDY_PACK),
                any(Pageable.class)
        )).thenReturn(List.of(openedEvent));
        when(studyPackRepository.findByIdAndOwnerUserId(openedPackId, userId)).thenReturn(Optional.of(openedPack));
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                eq(openedPackId),
                any(Pageable.class)
        )).thenReturn(List.of());

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isEqualTo(ContinueStudyingReason.RECENTLY_OPENED);
        assertThat(response.studyPackId()).isEqualTo(openedPackId.toString());
    }

    @Test
    void getContinueStudyingRecommendation_fallsBackToRecentlyCreated() {
        UUID userId = UUID.randomUUID();
        UUID createdPackId = UUID.randomUUID();
        StudyPackEntity createdPack = buildStudyPack(userId, createdPackId, "English Literature");

        when(quickReviewSessionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(List.of());
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

        when(quickReviewSessionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(activityEventRepository.findByUserIdAndActivityTypeAndStudyPackIdIsNotNullOrderByCreatedAtDesc(
                eq(userId),
                eq(ActivityType.OPENED_STUDY_PACK),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(studyPackRepository.findTopByOwnerUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());

        ContinueStudyingResponse response = dashboardService.getContinueStudyingRecommendation(userId);

        assertThat(response.reason()).isNull();
        assertThat(response.studyPackId()).isNull();
        assertThat(response.title()).isNull();
    }

    @Test
    void getContinueStudyingRecommendation_usesLatestCompletedSessionPerPackNotHistoricalBest() {
        UUID userId = UUID.randomUUID();
        UUID packWithOlderPerfectNewerWeakId = UUID.randomUUID();
        UUID otherPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        StudyPackEntity selectedPack = buildStudyPack(userId, packWithOlderPerfectNewerWeakId, "Programming");

        when(quickReviewSessionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(List.of(
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

        when(quickReviewSessionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.of(inProgress));
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

        when(quickReviewSessionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.of(inProgress));
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

        when(quickReviewSessionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(List.of(latestCompleted));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));

        TodayFocusResponse response = dashboardService.getTodayFocus(userId);

        assertThat(response.type()).isEqualTo(TodayFocusType.PRACTICE_WEAK_CONCEPT);
        assertThat(response.studyPackId()).isEqualTo(studyPackId.toString());
        assertThat(response.actionLabel()).isEqualTo("Practice Weak Areas");
    }

    @Test
    void getTodayFocus_returnsStudySuggestionWhenNoResumeOrWeakConceptContextExists() {
        UUID userId = UUID.randomUUID();

        when(quickReviewSessionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                userId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(List.of());

        TodayFocusResponse response = dashboardService.getTodayFocus(userId);

        assertThat(response.type()).isEqualTo(TodayFocusType.STUDY_SUGGESTION);
        assertThat(response.studyPackId()).isNull();
        assertThat(response.actionLabel()).isEqualTo("Open Library");
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

    private UserActivityEventEntity buildOpenedEvent(UUID userId, UUID studyPackId, OffsetDateTime createdAt) {
        return UserActivityEventEntityBuilder.anActivityEvent()
                .withUserId(userId)
                .withStudyPackId(studyPackId)
                .withActivityType(ActivityType.OPENED_STUDY_PACK)
                .withCreatedAt(createdAt)
                .build();
    }

    private BigDecimal bigDecimal(int value) {
        return BigDecimal.valueOf(value);
    }
}
