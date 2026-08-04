package com.studysnap.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExamGoalConfigTest {
    @Test
    void getFallbackCoursePrograms_returnsFailOpenExamValues() {
        assertThat(ExamGoalConfig.getFallbackCoursePrograms(ExamGoalConfig.ALE)).containsExactly("Architecture");
        assertThat(ExamGoalConfig.getFallbackCoursePrograms(ExamGoalConfig.PNLE)).containsExactly("Nursing");
        assertThat(ExamGoalConfig.getFallbackCoursePrograms(ExamGoalConfig.LET)).containsExactly("Education");
        assertThat(ExamGoalConfig.getFallbackCoursePrograms(ExamGoalConfig.CPALE)).containsExactly("Accountancy");
    }

    @Test
    void isValidSlug_acceptsOnlyConfiguredExamSlugs() {
        assertThat(ExamGoalConfig.isValidSlug(ExamGoalConfig.ALE)).isTrue();
        assertThat(ExamGoalConfig.isValidSlug("PNLE")).isTrue();
        assertThat(ExamGoalConfig.isValidSlug(" let ")).isTrue();
        assertThat(ExamGoalConfig.isValidSlug(ExamGoalConfig.CPALE)).isTrue();
        assertThat(ExamGoalConfig.isValidSlug(" CPALE ")).isTrue();
        assertThat(ExamGoalConfig.isValidSlug("cpa")).isFalse();
        assertThat(ExamGoalConfig.isValidSlug(null)).isFalse();
    }
}
