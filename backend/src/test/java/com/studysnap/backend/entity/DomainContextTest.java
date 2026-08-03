package com.studysnap.backend.entity;

import org.junit.jupiter.api.Test;

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
}
