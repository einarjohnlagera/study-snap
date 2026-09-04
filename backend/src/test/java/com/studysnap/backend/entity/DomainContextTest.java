package com.studysnap.backend.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DomainContextTest {

    @Test
    void fromString_isNullSafeCaseInsensitiveAndRejectsUnknownValues() {
        assertThat(DomainContext.fromString(null)).isNull();
        assertThat(DomainContext.fromString(" ")).isNull();
        assertThat(DomainContext.fromString("engineering_mathematics"))
                .isEqualTo(DomainContext.ENGINEERING_MATHEMATICS);
        assertThat(DomainContext.fromString("ENGINEERING_MATHEMATICS"))
                .isEqualTo(DomainContext.ENGINEERING_MATHEMATICS);
        assertThat(DomainContext.fromString("unknown")).isNull();
    }

    @Test
    void valuesExposeTheElevenRatifiedLabels() {
        assertThat(DomainContext.values()).extracting(DomainContext::getLabel).containsExactly(
                "Engineering Mathematics",
                "Engineering Sciences",
                "Civil Engineering",
                "Professional Practice & Regulation",
                "General Education",
                "Professional Education",
                "Nursing",
                "Accountancy",
                "Architectural Design",
                "History and Theory of Architecture",
                "Planning and Site Development"
        );
    }

    @ParameterizedTest
    @CsvSource({
            "ENGINEERING_MATHEMATICS, true",
            "ENGINEERING_SCIENCES, true",
            "CIVIL_ENGINEERING, true",
            "PROFESSIONAL_PRACTICE_AND_REGULATION, false",
            "GENERAL_EDUCATION, false",
            "PROFESSIONAL_EDUCATION, false",
            "NURSING, true",
            "ACCOUNTANCY, true",
            // ⚠️ v0.111.0's three values are false BY DECISION. Flipping one to true is
            // irreversible for every note generated meanwhile, because Study Packs never
            // auto-regenerate. This row is the guard: mutate the enum and this test names itself.
            "ARCHITECTURAL_DESIGN, false",
            "ARCHITECTURAL_HISTORY_AND_THEORY, false",
            "PLANNING_AND_SITE_DEVELOPMENT, false"
    })
    void declaresWhetherEachDomainContextIsQuantitative(DomainContext domainContext, boolean quantitative) {
        assertThat(domainContext.isQuantitative()).isEqualTo(quantitative);
    }
}
