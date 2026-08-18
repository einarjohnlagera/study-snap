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
    void valuesExposeTheEightRatifiedLabels() {
        assertThat(DomainContext.values()).extracting(DomainContext::getLabel).containsExactly(
                "Engineering Mathematics",
                "Engineering Sciences",
                "Civil Engineering",
                "Professional Practice & Regulation",
                "General Education",
                "Professional Education",
                "Nursing",
                "Accountancy"
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
            "ACCOUNTANCY, true"
    })
    void declaresWhetherEachDomainContextIsQuantitative(DomainContext domainContext, boolean quantitative) {
        assertThat(domainContext.isQuantitative()).isEqualTo(quantitative);
    }
}
