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

    private StudySnapProperties properties;
    private MePlanService mePlanService;

    @BeforeEach
    void setUp() {
        properties = new StudySnapProperties();
        properties.getPricing().setFreeMonthlyStudyPackLimit(10);
        properties.getPricing().setPremiumMonthlyStudyPackLimit(100);
        properties.getPricing().setFreeMonthlyChallengeQuizLimit(5);
        properties.getPricing().setPremiumMonthlyChallengeQuizLimit(50);
        properties.getPricing().setPremiumMonthlyAdaptivePracticeLimit(30);
        properties.getPricing().setFreeMonthlyOcrLimit(20);
        properties.getPricing().setPremiumMonthlyOcrLimit(100);
        properties.getPricing().setAdaptivePracticePremiumOnly(true);
        properties.getPricing().setDifficultySelectionPremiumOnly(true);
        mePlanService = new MePlanService(subscriptionService, userUsageService, properties);
    }

    @Test
    void returnsFreePlanLimitsUsageRemainingAndFeatureFlags() {
        UUID userId = UUID.randomUUID();
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new UserUsageService.MonthlyUsage(3, 2, 0, 5));

        MePlanResponse response = mePlanService.getPlan(userId);

        assertThat(response.plan()).isEqualTo(PlanType.FREE);
        assertThat(response.limits().studyPacksPerMonth()).isEqualTo(10);
        assertThat(response.limits().challengeQuizzesPerMonth()).isEqualTo(5);
        assertThat(response.limits().adaptivePracticePerMonth()).isZero();
        assertThat(response.limits().ocrPerMonth()).isEqualTo(20);
        assertThat(response.usage().studyPacksUsed()).isEqualTo(3);
        assertThat(response.usage().challengeQuizzesUsed()).isEqualTo(2);
        assertThat(response.usage().adaptivePracticeUsed()).isZero();
        assertThat(response.usage().ocrUsed()).isEqualTo(5);
        assertThat(response.remaining().studyPacksRemaining()).isEqualTo(7);
        assertThat(response.remaining().challengeQuizzesRemaining()).isEqualTo(3);
        assertThat(response.remaining().adaptivePracticeRemaining()).isZero();
        assertThat(response.remaining().ocrRemaining()).isEqualTo(15);
        assertThat(response.features().adaptivePracticeAvailable()).isFalse();
        assertThat(response.features().difficultySelectionAvailable()).isFalse();
        assertThat(response.features().fileUploadAvailable()).isTrue();
        assertThat(response.features().ocrAvailable()).isTrue();
    }

    @Test
    void returnsPremiumLimitsAndClampsRemainingAtZero() {
        UUID userId = UUID.randomUUID();
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PREMIUM);
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new UserUsageService.MonthlyUsage(101, 50, 33, 120));

        MePlanResponse response = mePlanService.getPlan(userId);

        assertThat(response.plan()).isEqualTo(PlanType.PREMIUM);
        assertThat(response.limits().studyPacksPerMonth()).isEqualTo(100);
        assertThat(response.limits().challengeQuizzesPerMonth()).isEqualTo(50);
        assertThat(response.limits().adaptivePracticePerMonth()).isEqualTo(30);
        assertThat(response.limits().ocrPerMonth()).isEqualTo(100);
        assertThat(response.remaining().studyPacksRemaining()).isZero();
        assertThat(response.remaining().challengeQuizzesRemaining()).isZero();
        assertThat(response.remaining().adaptivePracticeRemaining()).isZero();
        assertThat(response.remaining().ocrRemaining()).isZero();
        assertThat(response.features().adaptivePracticeAvailable()).isTrue();
        assertThat(response.features().difficultySelectionAvailable()).isTrue();
    }
}
