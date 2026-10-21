package com.studysnap.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ChallengeQuizStatementPairCutoffConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void blankCutoffBindsAsNullSoEveryLearnerOriginalStatementPairIsExcluded() {
        context.withPropertyValues("studysnap.challenge-quiz.statement-pair-legacy-cutoff=")
                .run(application -> assertThat(application.getBean(StudySnapProperties.class)
                        .getChallengeQuiz().getStatementPairLegacyCutoff()).isNull());
    }

    @Test
    void malformedCutoffFailsApplicationContextStartup() {
        context.withPropertyValues("studysnap.challenge-quiz.statement-pair-legacy-cutoff=not-a-date")
                .run(application -> assertThat(application.getStartupFailure()).isNotNull());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StudySnapProperties.class)
    static class PropertiesConfiguration {
    }
}
