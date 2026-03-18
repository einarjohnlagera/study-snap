package com.studysnap.backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizationUtilsTest {

    @Test
    void containsAlphaNumeric_returnsFalseForNullAndSymbolOnlyValues() {
        assertThat(TextNormalizationUtils.containsAlphaNumeric(null)).isFalse();
        assertThat(TextNormalizationUtils.containsAlphaNumeric("   ")).isFalse();
        assertThat(TextNormalizationUtils.containsAlphaNumeric("!@#$%^&*()")).isFalse();
    }

    @Test
    void containsAlphaNumeric_returnsTrueWhenLettersOrNumbersExist() {
        assertThat(TextNormalizationUtils.containsAlphaNumeric("Biology")).isTrue();
        assertThat(TextNormalizationUtils.containsAlphaNumeric("12345")).isTrue();
        assertThat(TextNormalizationUtils.containsAlphaNumeric("   #tag-1   ")).isTrue();
    }

    @Test
    void normalizeForDuplicateCheck_returnsEmptyStringForNullOrNonAlphanumericValues() {
        assertThat(TextNormalizationUtils.normalizeForDuplicateCheck(null)).isEqualTo("");
        assertThat(TextNormalizationUtils.normalizeForDuplicateCheck("!!!")).isEqualTo("");
    }

    @Test
    void normalizeForDuplicateCheck_lowercasesStripsPunctuationAndCollapsesSpaces() {
        assertThat(TextNormalizationUtils.normalizeForDuplicateCheck("  Photosynthesis: Basics!!!  "))
                .isEqualTo("photosynthesis basics");
        assertThat(TextNormalizationUtils.normalizeForDuplicateCheck("Cell-cycle   101"))
                .isEqualTo("cellcycle 101");
    }
}
