package com.studysnap.backend.config;

import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudySnapPropertiesTest {

    @Test
    void limitMegabyteValuesConvertToBytes() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getLimits().setFileUploadMaxSize(10);
        properties.getLimits().setTxtUploadMaxSize(1);
        properties.getLimits().setPdfUploadMaxSize(10);
        properties.getLimits().setDocxUploadMaxSize(5);

        assertThat(properties.getLimits().getFileUploadMaxSizeBytes()).isEqualTo(10L * 1024L * 1024L);
        assertThat(properties.getLimits().getTxtUploadMaxSizeBytes()).isEqualTo(1024L * 1024L);
        assertThat(properties.getLimits().getPdfUploadMaxSizeBytes()).isEqualTo(10L * 1024L * 1024L);
        assertThat(properties.getLimits().getDocxUploadMaxSizeBytes()).isEqualTo(5L * 1024L * 1024L);
    }

    @Test
    void docxExportLimitsUseTeacherProfileOverride() {
        StudySnapProperties.Pricing pricing = new StudySnapProperties().getPricing();

        assertThat(pricing.resolveMonthlyDocxExportLimit(PlanType.PLUS, ProfileType.TEACHER)).isNull();
        assertThat(pricing.resolveMonthlyDocxExportLimit(PlanType.PLUS, ProfileType.STUDENT)).isEqualTo(15);
        assertThat(pricing.resolveMonthlyDocxExportLimit(PlanType.FREE, ProfileType.TEACHER)).isEqualTo(10);
        assertThat(pricing.resolveMonthlyDocxExportLimit(PlanType.PRO, ProfileType.STUDENT)).isNull();
        assertThat(pricing.resolveMonthlyDocxExportLimit(PlanType.PRO, ProfileType.TEACHER)).isNull();
    }

    @Test
    void pdfExportLimitsStayPlanBasedForEveryProfile() {
        StudySnapProperties.Pricing pricing = new StudySnapProperties().getPricing();

        assertThat(pricing.resolveMonthlyPdfExportLimit(PlanType.PLUS)).isEqualTo(15);
        assertThat(pricing.resolveMonthlyPdfExportLimit(PlanType.PRO)).isNull();
    }

    @Test
    void adaptivePracticeUsesFreeMonthlyQuotaWhenNotProOnly() {
        StudySnapProperties.Pricing pricing = new StudySnapProperties().getPricing();

        assertThat(pricing.resolveMonthlyAdaptivePracticeLimit(PlanType.FREE)).isEqualTo(3);
        assertThat(pricing.isAdaptivePracticeAvailable(PlanType.FREE)).isTrue();
        assertThat(pricing.resolveMonthlyAdaptivePracticeLimit(PlanType.PLUS)).isEqualTo(10);
        assertThat(pricing.resolveMonthlyAdaptivePracticeLimit(PlanType.PRO)).isEqualTo(30);
    }

    @Test
    void adaptivePracticeProOnlyFlagStillBlocksLowerPlans() {
        StudySnapProperties.Pricing pricing = new StudySnapProperties().getPricing();
        pricing.setAdaptivePracticeProOnly(true);

        assertThat(pricing.resolveMonthlyAdaptivePracticeLimit(PlanType.FREE)).isZero();
        assertThat(pricing.isAdaptivePracticeAvailable(PlanType.FREE)).isFalse();
        assertThat(pricing.resolveMonthlyAdaptivePracticeLimit(PlanType.PLUS)).isZero();
        assertThat(pricing.isAdaptivePracticeAvailable(PlanType.PLUS)).isFalse();
        assertThat(pricing.resolveMonthlyAdaptivePracticeLimit(PlanType.PRO)).isEqualTo(30);
        assertThat(pricing.isAdaptivePracticeAvailable(PlanType.PRO)).isTrue();
    }

    @Test
    void askCompanionIsAvailableToPlusAndProWithTwentyMonthlySessions() {
        StudySnapProperties.Pricing pricing = new StudySnapProperties().getPricing();

        assertThat(pricing.resolveMonthlyAskCompanionLimit(PlanType.FREE)).isZero();
        assertThat(pricing.isAskCompanionAvailable(PlanType.FREE)).isFalse();
        assertThat(pricing.resolveMonthlyAskCompanionLimit(PlanType.PLUS)).isEqualTo(20);
        assertThat(pricing.isAskCompanionAvailable(PlanType.PLUS)).isTrue();
        assertThat(pricing.resolveMonthlyAskCompanionLimit(PlanType.PRO)).isEqualTo(20);
        assertThat(pricing.isAskCompanionAvailable(PlanType.PRO)).isTrue();
    }
}
