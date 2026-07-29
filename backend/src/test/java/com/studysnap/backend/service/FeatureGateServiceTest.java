package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.PlanType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FeatureGateServiceTest {

    @Test
    void adaptivePracticeIsAvailableForFreeWhenMonthlyQuotaIsPositive() {
        StudySnapProperties properties = new StudySnapProperties();
        FeatureGateService featureGateService = new FeatureGateService(mock(SubscriptionService.class), properties);

        assertThat(featureGateService.hasFeatureAccess(PlanType.FREE, Feature.ADAPTIVE_QUIZ)).isTrue();
    }

    @Test
    void challengeQuizCanStartOnlyWhileUsageIsBelowThePlanLimit() {
        StudySnapProperties properties = new StudySnapProperties();
        FeatureGateService featureGateService = new FeatureGateService(mock(SubscriptionService.class), properties);

        assertThat(featureGateService.canStartChallengeQuiz(PlanType.FREE, 19)).isTrue();
        assertThat(featureGateService.canStartChallengeQuiz(PlanType.FREE, 20)).isFalse();
    }

    @Test
    void askCompanionIsGatedToPlusAndPro() {
        StudySnapProperties properties = new StudySnapProperties();
        FeatureGateService featureGateService = new FeatureGateService(mock(SubscriptionService.class), properties);

        assertThat(featureGateService.hasFeatureAccess(PlanType.FREE, Feature.ASK_COMPANION)).isFalse();
        assertThat(featureGateService.hasFeatureAccess(PlanType.PLUS, Feature.ASK_COMPANION)).isTrue();
        assertThat(featureGateService.hasFeatureAccess(PlanType.PRO, Feature.ASK_COMPANION)).isTrue();
    }
}
