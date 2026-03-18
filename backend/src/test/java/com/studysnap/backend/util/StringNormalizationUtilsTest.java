package com.studysnap.backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StringNormalizationUtilsTest {

    @Test
    void isBlank_handlesNullWhitespaceAndText() {
        assertThat(StringNormalizationUtils.isBlank(null)).isTrue();
        assertThat(StringNormalizationUtils.isBlank("   ")).isTrue();
        assertThat(StringNormalizationUtils.isBlank("biology")).isFalse();
    }

    @Test
    void containsAlphaNumeric_returnsFalseForNullAndSymbolOnlyValues() {
        assertThat(StringNormalizationUtils.containsAlphaNumeric(null)).isFalse();
        assertThat(StringNormalizationUtils.containsAlphaNumeric("   ")).isFalse();
        assertThat(StringNormalizationUtils.containsAlphaNumeric("!@#$%^&*()")).isFalse();
    }

    @Test
    void containsAlphaNumeric_returnsTrueWhenLettersOrNumbersExist() {
        assertThat(StringNormalizationUtils.containsAlphaNumeric("Biology")).isTrue();
        assertThat(StringNormalizationUtils.containsAlphaNumeric("12345")).isTrue();
        assertThat(StringNormalizationUtils.containsAlphaNumeric("   #tag-1   ")).isTrue();
    }

    @Test
    void normalizeForDuplicateCheck_returnsEmptyStringForNullOrNonAlphanumericValues() {
        assertThat(StringNormalizationUtils.normalizeForDuplicateCheck(null)).isEqualTo("");
        assertThat(StringNormalizationUtils.normalizeForDuplicateCheck("!!!")).isEqualTo("");
    }

    @Test
    void normalizeForDuplicateCheck_lowercasesStripsPunctuationAndCollapsesSpaces() {
        assertThat(StringNormalizationUtils.normalizeForDuplicateCheck("  Photosynthesis: Basics!!!  "))
                .isEqualTo("photosynthesis basics");
        assertThat(StringNormalizationUtils.normalizeForDuplicateCheck("Cell-cycle   101"))
                .isEqualTo("cellcycle 101");
    }

    @Test
    void normalizeWhitespaceToSingleSpaceOrNull_behavesAsExpected() {
        assertThat(StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(null)).isNull();
        assertThat(StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull("   ")).isNull();
        assertThat(StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull("  biology   basics  "))
                .isEqualTo("biology basics");
        assertThat(StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull("photosynthesis\n  uses\tlight"))
                .isEqualTo("photosynthesis uses light");
    }

    @Test
    void countWords_and_hasWordCountBetween_workWithNormalizedWhitespace() {
        assertThat(StringNormalizationUtils.countWords(null)).isEqualTo(0);
        assertThat(StringNormalizationUtils.countWords("   ")).isEqualTo(0);
        assertThat(StringNormalizationUtils.countWords("cell respiration and atp")).isEqualTo(4);

        assertThat(StringNormalizationUtils.hasWordCountBetween("cell respiration and atp", 1, 4)).isTrue();
        assertThat(StringNormalizationUtils.hasWordCountBetween("cell respiration and atp", 5, 6)).isFalse();
    }
}
