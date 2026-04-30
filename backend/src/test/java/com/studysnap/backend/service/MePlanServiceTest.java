package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.MePlanResponse;
import com.studysnap.backend.entity.PlanType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MePlanServiceTest {

    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private UserUsageService userUsageService;
    @Mock
    private StudyPackUsageService studyPackUsageService;

    private MePlanService mePlanService;

    @BeforeEach
    void setUp() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getPricing().setFreeMonthlyStudyPackLimit(10);
        properties.getPricing().setProMonthlyStudyPackLimit(100);
        properties.getPricing().setFreeMonthlyChallengeQuizLimit(5);
        properties.getPricing().setProMonthlyChallengeQuizLimit(50);
        properties.getPricing().setProMonthlyAdaptivePracticeLimit(30);
        properties.getPricing().setFreeMonthlyOcrLimit(20);
        properties.getPricing().setProMonthlyOcrLimit(100);
        properties.getPricing().setFreeMonthlyNoteGenerationLimit(5);
        properties.getPricing().setProMonthlyNoteGenerationLimit(100);
        properties.getPricing().setFreeMonthlyExportLimit(2);
        properties.getPricing().setPlusMonthlyExportLimit(15);
        properties.getPricing().setProMonthlyExportLimit(-1);
        properties.getPricing().setAdaptivePracticeProOnly(true);
        properties.getPricing().setDifficultySelectionProOnly(true);
        mePlanService = new MePlanService(subscriptionService, userUsageService, studyPackUsageService, properties);
    }

    @Test
    void returnsFreePlanLimitsUsageRemainingAndFeatureFlags() {
        UUID userId = UUID.randomUUID();
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new UserUsageService.MonthlyUsage(
                        OffsetDateTime.parse("2026-03-10T00:00:00Z"),
                        OffsetDateTime.parse("2026-04-10T00:00:00Z"),
                        3,
                        2,
                        0,
                        5,
                        2,
                        1
                ));
        when(studyPackUsageService.resolveUsage(eq(userId), any(UserUsageService.MonthlyUsage.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.parse("2026-03-10T00:00:00Z"),
                        OffsetDateTime.parse("2026-04-10T00:00:00Z"),
                        3
                ));

        MePlanResponse response = mePlanService.getPlan(userId);

        assertThat(response.plan()).isEqualTo(PlanType.FREE);
        assertThat(response.usageCycle().startsAt()).isEqualTo(OffsetDateTime.parse("2026-03-10T00:00:00Z"));
        assertThat(response.usageCycle().endsAt()).isEqualTo(OffsetDateTime.parse("2026-04-10T00:00:00Z"));
        assertThat(response.limits().studyPacksPerMonth()).isEqualTo(10);
        assertThat(response.limits().challengeQuizzesPerMonth()).isEqualTo(5);
        assertThat(response.limits().adaptivePracticePerMonth()).isZero();
        assertThat(response.limits().ocrPerMonth()).isEqualTo(20);
        assertThat(response.limits().noteGenerationsPerMonth()).isEqualTo(5);
        assertThat(response.limits().exportsPerMonth()).isEqualTo(2);
        assertThat(response.usage().studyPacksUsed()).isEqualTo(3);
        assertThat(response.usage().challengeQuizzesUsed()).isEqualTo(2);
        assertThat(response.usage().adaptivePracticeUsed()).isZero();
        assertThat(response.usage().ocrUsed()).isEqualTo(5);
        assertThat(response.usage().noteGenerationsUsed()).isEqualTo(2);
        assertThat(response.usage().exportsUsed()).isEqualTo(1);
        assertThat(response.remaining().studyPacksRemaining()).isEqualTo(7);
        assertThat(response.remaining().challengeQuizzesRemaining()).isEqualTo(3);
        assertThat(response.remaining().adaptivePracticeRemaining()).isZero();
        assertThat(response.remaining().ocrRemaining()).isEqualTo(15);
        assertThat(response.remaining().noteGenerationsRemaining()).isEqualTo(3);
        assertThat(response.remaining().exportsRemaining()).isEqualTo(1);
        assertThat(response.features().adaptivePracticeAvailable()).isFalse();
        assertThat(response.features().difficultySelectionAvailable()).isFalse();
        assertThat(response.features().fileUploadAvailable()).isTrue();
        assertThat(response.features().ocrAvailable()).isTrue();
        assertThat(response.features().exportAvailable()).isTrue();
    }

    @Test
    void returnsPremiumLimitsAndClampsRemainingAtZero() {
        UUID userId = UUID.randomUUID();
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new UserUsageService.MonthlyUsage(
                        OffsetDateTime.parse("2026-03-20T00:00:00Z"),
                        OffsetDateTime.parse("2026-04-20T00:00:00Z"),
                        101,
                        50,
                        33,
                        120,
                        100,
                        42
                ));
        when(studyPackUsageService.resolveUsage(eq(userId), any(UserUsageService.MonthlyUsage.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.parse("2026-03-20T00:00:00Z"),
                        OffsetDateTime.parse("2026-04-20T00:00:00Z"),
                        101
                ));

        MePlanResponse response = mePlanService.getPlan(userId);

        assertThat(response.plan()).isEqualTo(PlanType.PRO);
        assertThat(response.usageCycle().endsAt()).isEqualTo(OffsetDateTime.parse("2026-04-20T00:00:00Z"));
        assertThat(response.limits().studyPacksPerMonth()).isEqualTo(100);
        assertThat(response.limits().challengeQuizzesPerMonth()).isEqualTo(50);
        assertThat(response.limits().adaptivePracticePerMonth()).isEqualTo(30);
        assertThat(response.limits().ocrPerMonth()).isEqualTo(100);
        assertThat(response.limits().noteGenerationsPerMonth()).isEqualTo(100);
        assertThat(response.limits().exportsPerMonth()).isNull();
        assertThat(response.remaining().studyPacksRemaining()).isZero();
        assertThat(response.remaining().challengeQuizzesRemaining()).isZero();
        assertThat(response.remaining().adaptivePracticeRemaining()).isZero();
        assertThat(response.remaining().ocrRemaining()).isZero();
        assertThat(response.remaining().noteGenerationsRemaining()).isZero();
        assertThat(response.remaining().exportsRemaining()).isNull();
        assertThat(response.features().adaptivePracticeAvailable()).isTrue();
        assertThat(response.features().difficultySelectionAvailable()).isTrue();
        assertThat(response.features().exportAvailable()).isTrue();
    }

    @Test
    void usesReconciledStudyPackUsageForPlanSummary() {
        UUID userId = UUID.randomUUID();
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new UserUsageService.MonthlyUsage(
                        OffsetDateTime.parse("2026-03-10T00:00:00Z"),
                        OffsetDateTime.parse("2026-04-10T00:00:00Z"),
                        4,
                        0,
                        0,
                        0,
                        0,
                        0
                ));
        when(studyPackUsageService.resolveUsage(eq(userId), any(UserUsageService.MonthlyUsage.class)))
                .thenReturn(new StudyPackUsageService.UsageSnapshot(
                        OffsetDateTime.parse("2026-03-10T00:00:00Z"),
                        OffsetDateTime.parse("2026-04-10T00:00:00Z"),
                        5
                ));

        MePlanResponse response = mePlanService.getPlan(userId);

        assertThat(response.usage().studyPacksUsed()).isEqualTo(5);
        assertThat(response.remaining().studyPacksRemaining()).isEqualTo(5);
    }
}
