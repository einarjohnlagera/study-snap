package com.studysnap.backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CourseProgramNormalizationUtilsTest {

    @Test
    void normalizeForStorage_trimsWhitespaceAndStandardizesDashSpacing() {
        assertThat(CourseProgramNormalizationUtils.normalizeForStorage("  Senior High-STEM  "))
                .isEqualTo("Senior High – STEM");
        assertThat(CourseProgramNormalizationUtils.normalizeForStorage("Civil Engineering -  Structural Design"))
                .isEqualTo("Civil Engineering – Structural Design");
        assertThat(CourseProgramNormalizationUtils.normalizeForStorage("Civil Engineering–Structural Design"))
                .isEqualTo("Civil Engineering – Structural Design");
    }

    @Test
    void normalizeForLookup_isCaseInsensitive() {
        assertThat(CourseProgramNormalizationUtils.normalizeForLookup("senior high - stem"))
                .isEqualTo(CourseProgramNormalizationUtils.normalizeForLookup("Senior High – STEM"));
    }
}
