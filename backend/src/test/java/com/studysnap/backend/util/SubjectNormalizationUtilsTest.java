package com.studysnap.backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectNormalizationUtilsTest {

    @Test
    void normalizeForStorage_trimsWhitespaceAndStandardizesDashSpacing() {
        assertThat(SubjectNormalizationUtils.normalizeForStorage("  Biology-Cell Division  "))
                .isEqualTo("Biology – Cell Division");
        assertThat(SubjectNormalizationUtils.normalizeForStorage("Biology -  Cell Division"))
                .isEqualTo("Biology – Cell Division");
        assertThat(SubjectNormalizationUtils.normalizeForStorage("Biology–Cell Division"))
                .isEqualTo("Biology – Cell Division");
    }

    @Test
    void normalizeForLookup_isCaseInsensitive() {
        assertThat(SubjectNormalizationUtils.normalizeForLookup("biology-cell division"))
                .isEqualTo(SubjectNormalizationUtils.normalizeForLookup("Biology – Cell Division"));
    }
}
