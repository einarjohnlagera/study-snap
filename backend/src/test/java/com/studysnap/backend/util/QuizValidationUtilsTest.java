package com.studysnap.backend.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuizValidationUtilsTest {

    @Test
    void hasInvalidChoices_detectsExactDuplicatesAndExtraWhitespace() {
        assertThat(QuizValidationUtils.hasInvalidChoices(List.of("A", "A", "B", "C"), null)).isTrue();
        assertThat(QuizValidationUtils.hasInvalidChoices(List.of("Derivative", "Derivative ", "Integral", "Limit"), null)).isTrue();
    }

    @Test
    void hasInvalidChoices_detectsDuplicatesAfterStrippingChoiceLabels() {
        assertThat(QuizValidationUtils.hasInvalidChoices(List.of(
                "A. Encapsulation",
                "Encapsulation",
                "B) Abstraction",
                "Polymorphism"
        ), null)).isTrue();
    }

    @Test
    void sanitizeChoiceTexts_stripsLeadingChoiceLabels() {
        assertThat(QuizValidationUtils.sanitizeChoiceTexts(List.of(
                "A. Encapsulation",
                "B) Abstraction",
                "c. Polymorphism",
                "D) Inheritance"
        ))).containsExactly("Encapsulation", "Abstraction", "Polymorphism", "Inheritance");
    }

    @Test
    void hasInvalidChoices_treatsDistinctMathExpressionsAsUnique() {
        assertThat(QuizValidationUtils.hasInvalidChoices(List.of(
                "u'v + uv'",
                "u'v - uv'",
                "(u/v)^2",
                "uv' - u'v"
        ), null)).isFalse();
    }

    @Test
    void hasInvalidChoices_detectsBlankChoicesAndWrongChoiceCount() {
        assertThat(QuizValidationUtils.hasInvalidChoices(List.of("A", "", "C", "D"), null)).isTrue();
        assertThat(QuizValidationUtils.hasInvalidChoices(List.of("A", "B", "C"), null)).isTrue();
        assertThat(QuizValidationUtils.hasInvalidChoices(List.of("A", "B", "C", "D"), null)).isFalse();
    }

    @Test
    void hasInvalidChoices_allowsTwoChoiceTrueFalseOnlyWhenMarked() {
        assertThat(QuizValidationUtils.hasInvalidChoices(List.of("True", "False"), "TRUE_FALSE")).isFalse();
        assertThat(QuizValidationUtils.hasInvalidChoices(List.of("True", "False"), null)).isTrue();
        assertThat(QuizValidationUtils.hasInvalidChoices(List.of("True", "False", "Maybe"), "TRUE_FALSE")).isTrue();
        assertThat(QuizValidationUtils.hasInvalidChoices(List.of("True", "True"), "TRUE_FALSE")).isTrue();
    }

    @Test
    void randomizeChoices_isDeterministicPerQuestionSeed() {
        List<String> original = List.of("A", "B", "C", "D");

        List<String> first = QuizValidationUtils.randomizeChoices(original, "What is ATP?");
        List<String> second = QuizValidationUtils.randomizeChoices(original, "What is ATP?");
        List<String> third = QuizValidationUtils.randomizeChoices(original, "What is DNA?");

        assertThat(first).containsExactlyInAnyOrderElementsOf(original);
        assertThat(second).containsExactlyInAnyOrderElementsOf(original);
        assertThat(third).containsExactlyInAnyOrderElementsOf(original);
        assertThat(first).containsExactlyElementsOf(second);
    }

    @Test
    void randomizeChoices_doesNotMutateOriginalList() {
        List<String> original = new java.util.ArrayList<>(List.of("A", "B", "C", "D"));

        List<String> shuffled = QuizValidationUtils.randomizeChoices(original, "What is ATP?");

        assertThat(original).containsExactly("A", "B", "C", "D");
        assertThat(shuffled).containsExactlyInAnyOrder("A", "B", "C", "D");
    }

    @Test
    void buildFallbackExplanation_returnsExpectedText() {
        assertThat(QuizValidationUtils.buildFallbackExplanation(null))
                .isEqualTo("Review this question in your notes.");
        assertThat(QuizValidationUtils.buildFallbackExplanation("   "))
                .isEqualTo("Review this question in your notes.");
        assertThat(QuizValidationUtils.buildFallbackExplanation("Photosynthesis"))
                .isEqualTo("Review the Photosynthesis concept in your notes.");
    }
}
