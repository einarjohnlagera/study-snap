package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.MePlanResponse;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.repository.UserRepository;
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
    @Mock
    private UserRepository userRepository;

    private MePlanService mePlanService;

    @BeforeEach
    void setUp() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getPricing().setFreeMonthlyStudyPackLimit(10);
        properties.getPricing().setProMonthlyStudyPackLimit(100);
        properties.getPricing().setFreeMonthlyChallengeQuizLimit(20);
        properties.getPricing().setPlusMonthlyChallengeQuizLimit(100);
        properties.getPricing().setProMonthlyChallengeQuizLimit(200);
        properties.getPricing().setFreeMonthlyAdaptivePracticeLimit(3);
        properties.getPricing().setProMonthlyAdaptivePracticeLimit(30);
        properties.getPricing().setProMonthlyLongExamLimit(10);
        properties.getPricing().setProMonthlyBoardExamLimit(5);
        properties.getPricing().setFreeMonthlyOcrLimit(20);
        properties.getPricing().setProMonthlyOcrLimit(100);
        properties.getPricing().setFreeMonthlyNoteGenerationLimit(5);
        properties.getPricing().setProMonthlyNoteGenerationLimit(100);
        properties.getPricing().setFreeMonthlyDocxExportLimit(2);
        properties.getPricing().setPlusMonthlyDocxExportLimit(15);
        properties.getPricing().setProMonthlyDocxExportLimit(-1);
        properties.getPricing().setFreeMonthlyPdfExportLimit(2);
        properties.getPricing().setPlusMonthlyPdfExportLimit(15);
        properties.getPricing().setProMonthlyPdfExportLimit(-1);
        properties.getPricing().setAdaptivePracticeProOnly(false);
        FeatureGateService featureGateService = new FeatureGateService(subscriptionService, properties);
        mePlanService = new MePlanService(
                subscriptionService,
                userUsageService,
                studyPackUsageService,
                userRepository,
                featureGateService,
                properties
        );
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
        assertThat(response.limits().challengeQuizzesPerMonth()).isEqualTo(20);
        assertThat(response.limits().adaptivePracticePerMonth()).isEqualTo(3);
        assertThat(response.limits().longExamPerMonth()).isZero();
        assertThat(response.limits().boardExamPerMonth()).isZero();
        assertThat(response.limits().ocrPerMonth()).isEqualTo(20);
        assertThat(response.limits().noteGenerationsPerMonth()).isEqualTo(5);
        assertThat(response.limits().docxExportsPerMonth()).isEqualTo(2);
        assertThat(response.limits().pdfExportsPerMonth()).isEqualTo(2);
        assertThat(response.usage().studyPacksUsed()).isEqualTo(3);
        assertThat(response.usage().challengeQuizzesUsed()).isEqualTo(2);
        assertThat(response.usage().adaptivePracticeUsed()).isZero();
        assertThat(response.usage().longExamUsed()).isZero();
        assertThat(response.usage().boardExamUsed()).isZero();
        assertThat(response.usage().ocrUsed()).isEqualTo(5);
        assertThat(response.usage().noteGenerationsUsed()).isEqualTo(2);
        assertThat(response.usage().docxExportsUsed()).isEqualTo(1);
        assertThat(response.usage().pdfExportsUsed()).isZero();
        assertThat(response.remaining().studyPacksRemaining()).isEqualTo(7);
        assertThat(response.remaining().challengeQuizzesRemaining()).isEqualTo(18);
        assertThat(response.remaining().adaptivePracticeRemaining()).isEqualTo(3);
        assertThat(response.remaining().longExamRemaining()).isZero();
        assertThat(response.remaining().boardExamRemaining()).isZero();
        assertThat(response.remaining().ocrRemaining()).isEqualTo(15);
        assertThat(response.remaining().noteGenerationsRemaining()).isEqualTo(3);
        assertThat(response.remaining().docxExportsRemaining()).isEqualTo(1);
        assertThat(response.remaining().pdfExportsRemaining()).isEqualTo(2);
        assertThat(response.features().adaptivePracticeAvailable()).isTrue();
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
                        201,
                        33,
                        0,
                        120,
                        100,
                        42,
                        8,
                        5
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
        assertThat(response.limits().challengeQuizzesPerMonth()).isEqualTo(200);
        assertThat(response.limits().adaptivePracticePerMonth()).isEqualTo(30);
        assertThat(response.limits().longExamPerMonth()).isEqualTo(10);
        assertThat(response.limits().boardExamPerMonth()).isEqualTo(5);
        assertThat(response.limits().ocrPerMonth()).isEqualTo(100);
        assertThat(response.limits().noteGenerationsPerMonth()).isEqualTo(100);
        assertThat(response.limits().docxExportsPerMonth()).isNull();
        assertThat(response.limits().pdfExportsPerMonth()).isNull();
        assertThat(response.remaining().studyPacksRemaining()).isZero();
        assertThat(response.remaining().challengeQuizzesRemaining()).isZero();
        assertThat(response.remaining().adaptivePracticeRemaining()).isZero();
        assertThat(response.usage().longExamUsed()).isEqualTo(8);
        assertThat(response.usage().boardExamUsed()).isEqualTo(5);
        assertThat(response.remaining().longExamRemaining()).isEqualTo(2);
        assertThat(response.remaining().boardExamRemaining()).isZero();
        assertThat(response.remaining().ocrRemaining()).isZero();
        assertThat(response.remaining().noteGenerationsRemaining()).isZero();
        assertThat(response.remaining().docxExportsRemaining()).isNull();
        assertThat(response.remaining().pdfExportsRemaining()).isNull();
        assertThat(response.features().adaptivePracticeAvailable()).isTrue();
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

    @Test
    void resolvesRemainingNoteGenerationsFromPlanLimitAndMonthlyUsage() {
        UUID userId = UUID.randomUUID();
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new UserUsageService.MonthlyUsage(
                        OffsetDateTime.parse("2026-03-10T00:00:00Z"),
                        OffsetDateTime.parse("2026-04-10T00:00:00Z"),
                        0,
                        0,
                        0,
                        0,
                        3,
                        0
                ));

        int remaining = mePlanService.getNoteGenerationsRemaining(userId);

        assertThat(remaining).isEqualTo(2);
    }
}
