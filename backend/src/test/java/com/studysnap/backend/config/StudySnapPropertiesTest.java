package com.studysnap.backend.config;

import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudySnapPropertiesTest {

    @Test
    void applicationYamlBindsBracketedModelPriceKeysAndRates() throws IOException {
        // The test classpath has its own application.yaml; read the production file explicitly.
        var sources = new YamlPropertySourceLoader().load(
                "application", new FileSystemResource("src/main/resources/application.yaml"));
        var binder = new Binder(ConfigurationPropertySources.from(sources.get(0)));
        Map<String, StudySnapProperties.ModelPrice> prices = binder.bind(
                "studysnap.curation-cost-alert.model-prices",
                Bindable.mapOf(String.class, StudySnapProperties.ModelPrice.class))
                .orElseThrow(() -> new AssertionError("application.yaml model prices did not bind"));

        assertThat(prices).containsKeys("gpt-6.1-sol", "gpt-4.1-mini");
        assertThat(prices).doesNotContainKeys("[gpt-6.1-sol]", "[gpt-4.1-mini]");
        assertThat(prices.get("gpt-6.1-sol").getInput()).isEqualByComparingTo(new BigDecimal("2.00"));
        assertThat(prices.get("gpt-6.1-sol").getCachedInput()).isNull();
        assertThat(prices.get("gpt-6.1-sol").getOutput()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(prices.get("gpt-4.1-mini").getInput()).isEqualByComparingTo(new BigDecimal("0.40"));
        assertThat(prices.get("gpt-4.1-mini").getCachedInput()).isNull();
        assertThat(prices.get("gpt-4.1-mini").getOutput()).isEqualByComparingTo(new BigDecimal("1.60"));
    }

    @Test
    void linkedLearnerGuardianConsentUsesConservativeConfigurableDefault() {
        StudySnapProperties.LinkedLearners linkedLearners = new StudySnapProperties().getLinkedLearners();

        assertThat(linkedLearners.getGuardianConsentMaxAge()).isEqualTo(17);
        linkedLearners.setGuardianConsentMaxAge(15);
        assertThat(linkedLearners.getGuardianConsentMaxAge()).isEqualTo(15);
    }

    @Test
    void linkedLearnerRequestTtlCannotUndercutTheThirtyDayCheckpointBound() {
        StudySnapProperties.LinkedLearners linkedLearners = new StudySnapProperties().getLinkedLearners();

        assertThat(linkedLearners.getRequestTtlDays()).isEqualTo(30);
        assertThatThrownBy(() -> linkedLearners.setRequestTtlDays(29))
                .isInstanceOf(IllegalArgumentException.class);
        linkedLearners.setRequestTtlDays(45);
        assertThat(linkedLearners.getRequestTtlDays()).isEqualTo(45);
    }

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
    void generationRecoveryUsesConservativeConfigurableDefaults() {
        StudySnapProperties.Generation generation = new StudySnapProperties().getGeneration();

        assertThat(generation.getRecoveryCron()).isEqualTo("0 */10 * * * *");
        assertThat(generation.isEnabled()).isTrue();
        assertThat(generation.getRecoveryBatchSize()).isEqualTo(200);
        assertThat(generation.getPoolPendingBoundMinutes()).isEqualTo(60);
        assertThat(generation.getPoolGeneratingBoundMinutes()).isEqualTo(60);
        assertThat(generation.getLongExamSessionBoundMinutes()).isEqualTo(30);
        assertThat(generation.getNoteBoundMinutes()).isEqualTo(120);
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
