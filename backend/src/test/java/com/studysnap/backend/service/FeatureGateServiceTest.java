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
}
