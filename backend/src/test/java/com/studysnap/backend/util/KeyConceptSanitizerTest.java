package com.studysnap.backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeyConceptSanitizerTest {

    // --- tryRepair ---

    @Test
    void tryRepair_nullInput_returnsNull() {
        assertThat(KeyConceptSanitizer.tryRepair(null, 4)).isNull();
    }

    @Test
    void tryRepair_blankInput_returnsNull() {
        assertThat(KeyConceptSanitizer.tryRepair("   ", 4)).isNull();
    }

    @Test
    void tryRepair_withinLimit_returnsUnchanged() {
        // Within limit and no filler → candidate passes through unchanged
        assertThat(KeyConceptSanitizer.tryRepair("Ohm's Law", 4)).isEqualTo("Ohm's Law");
    }

    @Test
    void tryRepair_exactlyAtLimit_returnsUnchanged() {
        // Exactly at limit → no truncation needed → candidate returned as-is
        assertThat(KeyConceptSanitizer.tryRepair("ATP Energy Production Cell", 4)).isEqualTo("ATP Energy Production Cell");
    }

    @Test
    void tryRepair_fillerPrefix_relationshipBetween_stripped() {
        String result = KeyConceptSanitizer.tryRepair("relationship between voltage and current", 4);
        // After stripping "relationship between", left with "voltage and current" = 3 words → fits
        assertThat(result).isEqualTo("voltage and current");
    }

    @Test
    void tryRepair_fillerPrefix_usingThe_stripped() {
        String result = KeyConceptSanitizer.tryRepair("using the power formula to calculate", 4);
        // After stripping "using the", left with "power formula to calculate" = 4 words → fits
        assertThat(result).isEqualTo("power formula to calculate");
    }

    @Test
    void tryRepair_fillerPrefix_usingA_stripped() {
        String result = KeyConceptSanitizer.tryRepair("using a resistance formula here now", 4);
        // After stripping "using a", left with "resistance formula here now" = 4 words → fits
        assertThat(result).isEqualTo("resistance formula here now");
    }

    @Test
    void tryRepair_fillerPrefix_theRoleOf_stripped() {
        String result = KeyConceptSanitizer.tryRepair("the role of mitochondria in cells", 4);
        // After stripping "the role of", left with "mitochondria in cells" = 3 words → fits
        assertThat(result).isEqualTo("mitochondria in cells");
    }

    @Test
    void tryRepair_fillerPrefix_definitionOf_stripped() {
        String result = KeyConceptSanitizer.tryRepair("definition of electrical resistance here", 4);
        // After stripping "definition of", left with "electrical resistance here" = 3 words
        assertThat(result).isEqualTo("electrical resistance here");
    }

    @Test
    void tryRepair_fillerPrefix_overviewOf_stripped() {
        String result = KeyConceptSanitizer.tryRepair("overview of object oriented programming paradigm", 4);
        // After stripping "overview of", left with "object oriented programming paradigm" = 4 words
        assertThat(result).isEqualTo("object oriented programming paradigm");
    }

    @Test
    void tryRepair_fillerPrefix_theConceptOf_stripped() {
        String result = KeyConceptSanitizer.tryRepair("the concept of recursion in algorithms", 4);
        // After stripping "the concept of", left with "recursion in algorithms" = 3 words
        assertThat(result).isEqualTo("recursion in algorithms");
    }

    @Test
    void tryRepair_fillerPrefix_howTo_stripped() {
        String result = KeyConceptSanitizer.tryRepair("how to calculate resistance value now", 4);
        // After stripping "how to", left with "calculate resistance value now" = 4 words
        assertThat(result).isEqualTo("calculate resistance value now");
    }

    @Test
    void tryRepair_noFillerButOverlong_truncatesToMaxWords() {
        String result = KeyConceptSanitizer.tryRepair("Voltage Current Resistance Power Calculation Formula", 4);
        assertThat(result).isEqualTo("Voltage Current Resistance Power");
    }

    @Test
    void tryRepair_fillerStrippedStillOverlong_truncatesToMaxWords() {
        // After stripping "relationship between", still > 4 words → truncate
        String result = KeyConceptSanitizer.tryRepair("relationship between voltage current resistance and power formula", 4);
        assertThat(result).isNotNull();
        assertThat(result.split("\\s+")).hasSizeLessThanOrEqualTo(4);
    }

    @Test
    void tryRepair_singleWord_returnsUnchanged() {
        assertThat(KeyConceptSanitizer.tryRepair("Glycolysis", 4)).isEqualTo("Glycolysis");
    }

    @Test
    void tryRepair_withSymbolsInPhrase_withinLimit_returnsUnchanged() {
        // 4 words exactly → no truncation → returned unchanged
        assertThat(KeyConceptSanitizer.tryRepair("Ohm's Law and Voltage", 4)).isEqualTo("Ohm's Law and Voltage");
    }

    @Test
    void tryRepair_repeatedWords_truncatesToMaxWords() {
        String result = KeyConceptSanitizer.tryRepair("power power power power power power", 4);
        assertThat(result).isEqualTo("power power power power");
    }

    @Test
    void tryRepair_fillerPrefixCaseInsensitive_stripped() {
        // Filler prefix matching must be case-insensitive
        String result = KeyConceptSanitizer.tryRepair("Relationship Between voltage and current", 4);
        assertThat(result).isEqualTo("voltage and current");
    }
}
