package com.studysnap.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExamGoalConfigTest {
    @Test
    void getCoursePrograms_returnsConfiguredExamAliases() {
        assertThat(ExamGoalConfig.getCoursePrograms("ale")).containsExactly("Architecture");
        assertThat(ExamGoalConfig.getCoursePrograms("pnle")).containsExactly("Nursing", "Medical – Surgical Nursing");
        assertThat(ExamGoalConfig.getCoursePrograms("let")).containsExactly("Education");
        assertThat(ExamGoalConfig.getCoursePrograms("cpale")).containsExactly("Accountancy");
    }

    @Test
    void getCoursePrograms_preservesEnDashInMedicalSurgicalNursingAlias() {
        String medicalSurgicalNursing = ExamGoalConfig.getCoursePrograms("pnle").get(1);

        assertThat(medicalSurgicalNursing).contains("\u2013");
        assertThat(medicalSurgicalNursing).doesNotContain(" - ");
    }

    @Test
    void isValidSlug_acceptsOnlyConfiguredExamSlugs() {
        assertThat(ExamGoalConfig.isValidSlug("ale")).isTrue();
        assertThat(ExamGoalConfig.isValidSlug("PNLE")).isTrue();
        assertThat(ExamGoalConfig.isValidSlug(" let ")).isTrue();
        assertThat(ExamGoalConfig.isValidSlug("cpale")).isTrue();
        assertThat(ExamGoalConfig.isValidSlug(" CPALE ")).isTrue();
        assertThat(ExamGoalConfig.isValidSlug("cpa")).isFalse();
        assertThat(ExamGoalConfig.isValidSlug(null)).isFalse();
    }
}
