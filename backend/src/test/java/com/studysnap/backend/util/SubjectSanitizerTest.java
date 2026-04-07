package com.studysnap.backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectSanitizerTest {

    // --- tryRepair ---
    // tryRepair is only for overlong inputs; returns null when no repair is needed or repair fails.

    @Test
    void tryRepair_nullInput_returnsNull() {
        assertThat(SubjectSanitizer.tryRepair(null, 6)).isNull();
    }

    @Test
    void tryRepair_withinLimit_returnsNull() {
        // Within limit → nothing to repair
        assertThat(SubjectSanitizer.tryRepair("Integral Calculus", 6)).isNull();
    }

    @Test
    void tryRepair_exactlyAtLimit_returnsNull() {
        // Exactly at limit → not overlong → null
        String input = "Object Oriented Programming Design Patterns Basics";
        assertThat(SubjectSanitizer.tryRepair(input, 6)).isNull();
    }

    @Test
    void tryRepair_structuredSubtopic_trimsSubtopicToFitLimit() {
        // "Electrical Engineering – Voltage Current Resistance Power" = 7 word-tokens including "–"
        String input = "Electrical Engineering – Voltage Current Resistance Power";
        String result = SubjectSanitizer.tryRepair(input, 6);
        // primary=2, dash slot=1, subtopic max=3 → "Voltage Current Resistance"
        assertThat(result).isEqualTo("Electrical Engineering – Voltage Current Resistance");
    }

    @Test
    void tryRepair_structuredSubtopic_stripsTrailingCommaFromSubtopic() {
        // "Biology – Cell Division, Meiosis, Mitosis" = 6 tokens but has comma-separated extras
        // primary=1, dash slot=1, subtopic max=4 words; "Cell Division, Meiosis, Mitosis" → trim to 4
        String input = "Biology – Cell Division, Meiosis, Mitosis, Extra";
        String result = SubjectSanitizer.tryRepair(input, 6);
        assertThat(result).isNotNull().doesNotEndWith(",").doesNotEndWith(";");
    }

    @Test
    void tryRepair_plainWayOverlong_truncatesToMaxWords() {
        // 8 words → truncate to 6
        String input = "Electrical Engineering Voltage Current Resistance Power Formula Relationships";
        String result = SubjectSanitizer.tryRepair(input, 6);
        assertThat(result).isEqualTo("Electrical Engineering Voltage Current Resistance Power");
    }

    @Test
    void tryRepair_primaryExceedsLimit_fallsBackToPlainTruncation() {
        // primary alone uses maxWords slots → maxSubtopicWords ≤ 0 → structured trim skipped → plain truncation
        // "One Two Three Four Five Six – Ohm" = 8 tokens; primary=6 words, no room for subtopic
        String input = "One Two Three Four Five Six – Ohm";
        String result = SubjectSanitizer.tryRepair(input, 6);
        assertThat(result).isNotNull();
        assertThat(result.split("\\s+")).hasSizeLessThanOrEqualTo(6);
    }

    // --- isOverlyBroad ---

    @Test
    void isOverlyBroad_knownBroadLabels_returnsTrue() {
        assertThat(SubjectSanitizer.isOverlyBroad("medicine")).isTrue();
        assertThat(SubjectSanitizer.isOverlyBroad("engineering")).isTrue();
        assertThat(SubjectSanitizer.isOverlyBroad("law")).isTrue();
        assertThat(SubjectSanitizer.isOverlyBroad("business")).isTrue();
        assertThat(SubjectSanitizer.isOverlyBroad("education")).isTrue();
    }

    @Test
    void isOverlyBroad_caseInsensitive_returnsTrue() {
        assertThat(SubjectSanitizer.isOverlyBroad("Medicine")).isTrue();
        assertThat(SubjectSanitizer.isOverlyBroad("ENGINEERING")).isTrue();
        assertThat(SubjectSanitizer.isOverlyBroad("Law")).isTrue();
    }

    @Test
    void isOverlyBroad_specificSubject_returnsFalse() {
        assertThat(SubjectSanitizer.isOverlyBroad("Integral Calculus")).isFalse();
        assertThat(SubjectSanitizer.isOverlyBroad("Circuit Theory")).isFalse();
        assertThat(SubjectSanitizer.isOverlyBroad("Criminal Procedure")).isFalse();
        assertThat(SubjectSanitizer.isOverlyBroad("Pharmacology")).isFalse();
    }

    @Test
    void isOverlyBroad_nullInput_returnsFalse() {
        assertThat(SubjectSanitizer.isOverlyBroad(null)).isFalse();
    }

    @Test
    void isOverlyBroad_subjectWithSubtopic_returnsFalse() {
        // "Engineering – Ohm's Law" has a subtopic qualifier → not just the broad label
        assertThat(SubjectSanitizer.isOverlyBroad("Engineering – Ohm's Law")).isFalse();
    }

    // --- matchesBroadCourseProgram ---

    @Test
    void matchesBroadCourseProgram_exactMatch_returnsTrue() {
        assertThat(SubjectSanitizer.matchesBroadCourseProgram("Biology", "Biology")).isTrue();
    }

    @Test
    void matchesBroadCourseProgram_caseInsensitiveMatch_returnsTrue() {
        assertThat(SubjectSanitizer.matchesBroadCourseProgram("biology", "Biology")).isTrue();
        assertThat(SubjectSanitizer.matchesBroadCourseProgram("BIOLOGY", "biology")).isTrue();
    }

    @Test
    void matchesBroadCourseProgram_subjectHasSubtopicWithEmDash_returnsFalse() {
        assertThat(SubjectSanitizer.matchesBroadCourseProgram("Biology – Cell Division", "Biology")).isFalse();
    }

    @Test
    void matchesBroadCourseProgram_subjectHasSubtopicWithHyphen_returnsFalse() {
        assertThat(SubjectSanitizer.matchesBroadCourseProgram("Biology - Cell Division", "Biology")).isFalse();
    }

    @Test
    void matchesBroadCourseProgram_differentSubject_returnsFalse() {
        assertThat(SubjectSanitizer.matchesBroadCourseProgram("Integral Calculus", "Mathematics")).isFalse();
    }

    @Test
    void matchesBroadCourseProgram_nullSubject_returnsFalse() {
        assertThat(SubjectSanitizer.matchesBroadCourseProgram(null, "Biology")).isFalse();
    }

    @Test
    void matchesBroadCourseProgram_nullCourseProgram_returnsFalse() {
        assertThat(SubjectSanitizer.matchesBroadCourseProgram("Biology", null)).isFalse();
    }

    @Test
    void matchesBroadCourseProgram_blankCourseProgram_returnsFalse() {
        assertThat(SubjectSanitizer.matchesBroadCourseProgram("Biology", "   ")).isFalse();
    }
}
