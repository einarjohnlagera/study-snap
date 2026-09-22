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

    // An IDENTIFICATION answer is graded by exact normalized string equality, so a stem asking for the
    // notation ITSELF cannot be graded fairly -- x^2 + y^2, x² + y² and y² + x² are all correct and none
    // match as text. This is the exact question that shipped and marked the correct answer wrong.
    @Test
    void isFormatStemMismatch_rejectsIdentificationAskingForAnAlgebraicExpression() {
        assertThat(QuizValidationUtils.isFormatStemMismatch(
                "Identify the algebraic expression for the sum of the squares of two variables $x$ and $y$.",
                List.of(),
                "IDENTIFICATION"
        )).isTrue();
    }

    // The rule is about the ANSWER'S FORM, not the subject -- it must cover every subject NoteLib serves.
    @Test
    void isFormatStemMismatch_rejectsIdentificationAskingForNotationInAnySubject() {
        assertThat(QuizValidationUtils.isFormatStemMismatch(
                "Identify the chemical formula for water.", List.of(), "IDENTIFICATION"
        )).isTrue();
        assertThat(QuizValidationUtils.isFormatStemMismatch(
                "Write the equation for Newton's Second Law.", List.of(), "IDENTIFICATION"
        )).isTrue();
        assertThat(QuizValidationUtils.isFormatStemMismatch(
                "State the mathematical expression for kinetic energy.", List.of(), "IDENTIFICATION"
        )).isTrue();
    }

    // False-positive guard. A formula's NAME is a perfectly valid IDENTIFICATION answer -- only the
    // notation itself is not. Over-rejecting would silently delete a legitimate question format.
    @Test
    void isFormatStemMismatch_allowsIdentificationAskingForANameOrTerm() {
        assertThat(QuizValidationUtils.isFormatStemMismatch(
                "Which law states that force equals mass times acceleration?", List.of(), "IDENTIFICATION"
        )).isFalse();
        assertThat(QuizValidationUtils.isFormatStemMismatch(
                "Identify the organelle that produces ATP.", List.of(), "IDENTIFICATION"
        )).isFalse();
        assertThat(QuizValidationUtils.isFormatStemMismatch(
                "Name the process by which plants convert light into chemical energy.", List.of(), "IDENTIFICATION"
        )).isFalse();
    }

    // The IDENTIFICATION branch must not change how other formats are judged.
    @Test
    void isFormatStemMismatch_leavesNonIdentificationFormatsUnchanged() {
        assertThat(QuizValidationUtils.isFormatStemMismatch(
                "Identify the algebraic expression for the sum of the squares.",
                List.of("A", "B", "C", "D"),
                "MULTI_CHOICE"
        )).isFalse();
    }

    @Test
    void isFormatStemMismatch_detectsTrueFalseWhichIsCorrectStem() {
        assertThat(QuizValidationUtils.isFormatStemMismatch(
                "Which is correct about photosynthesis?",
                List.of("True", "False"),
                "TRUE_FALSE"
        )).isTrue();
    }

    @Test
    void isFormatStemMismatch_detectsWhichOfTheFollowingStem() {
        assertThat(QuizValidationUtils.isFormatStemMismatch(
                "Which of the following describes glycolysis?",
                List.of("True", "False"),
                "TRUE_FALSE"
        )).isTrue();
    }

    @Test
    void isFormatStemMismatch_detectsMultiStatementWhichIsCorrectStem() {
        assertThat(QuizValidationUtils.isFormatStemMismatch(
                "Statement 1: ATP stores usable energy. Statement 2: Oxygen is the final electron acceptor. Which is correct?",
                List.of("True", "False"),
                "TRUE_FALSE"
        )).isTrue();
    }

    @Test
    void isFormatStemMismatch_detectsAllExceptStem() {
        assertThat(QuizValidationUtils.isFormatStemMismatch(
                "All of the following are properties of enzymes except one.",
                List.of("True", "False"),
                "TRUE_FALSE"
        )).isTrue();
    }

    @Test
    void isFormatStemMismatch_detectsTrueFalseChoicesEvenWithoutFormat() {
        assertThat(QuizValidationUtils.isFormatStemMismatch(
                "Which statement best describes mitosis?",
                List.of("False", "True"),
                null
        )).isTrue();
    }

    @Test
    void isFormatStemMismatch_allowsLegitimateDeclarativeTrueFalse() {
        assertThat(QuizValidationUtils.isFormatStemMismatch(
                "Statement: Passive cooling reduces HVAC dependency. — True or False?",
                List.of("True", "False"),
                "TRUE_FALSE"
        )).isFalse();
    }

    @Test
    void isFormatStemMismatch_allowsNormalMcqAndOtherFormats() {
        assertThat(QuizValidationUtils.isFormatStemMismatch(
                "Which is correct about enzymes?",
                List.of("They lower activation energy", "They become reactants", "They remove products", "They stop reactions"),
                "MCQ"
        )).isFalse();
        assertThat(QuizValidationUtils.isFormatStemMismatch(
                "Which properties apply to enzymes?",
                List.of("Reusable", "Specific", "Lower activation energy", "Always consumed"),
                "MULTI_SELECT"
        )).isFalse();
        assertThat(QuizValidationUtils.isFormatStemMismatch(
                "Match the law to its description.",
                List.of("Ohm's Law", "Kirchhoff's Law", "Faraday's Law", "Lenz's Law"),
                "MATCHING"
        )).isFalse();
    }

    @Test
    void hasInvalidCorrectIndices_validatesMultiSelectCorrectIndexCountAndRange() {
        List<String> choices = List.of("A", "B", "C", "D");

        assertThat(QuizValidationUtils.hasInvalidCorrectIndices(List.of(0), choices, "MULTI_SELECT")).isTrue();
        assertThat(QuizValidationUtils.hasInvalidCorrectIndices(List.of(0, 2), choices, "MULTI_SELECT")).isFalse();
        assertThat(QuizValidationUtils.hasInvalidCorrectIndices(List.of(0, 1, 2), choices, "MULTI_SELECT")).isFalse();
        assertThat(QuizValidationUtils.hasInvalidCorrectIndices(List.of(0, 4), choices, "MULTI_SELECT")).isTrue();
        assertThat(QuizValidationUtils.hasInvalidCorrectIndices(List.of(0, 0), choices, "MULTI_SELECT")).isTrue();
        assertThat(QuizValidationUtils.hasInvalidCorrectIndices(null, choices, "MCQ")).isFalse();
    }

    @Test
    void answerExplanationConsistency_rejectsReportedIncidentAndAcceptsCorrectKey() {
        List<String> choices = List.of("15%", "30%", "25%", "10%");
        String explanation = "The increase is (15 / 50) × 100% = 30%.";

        assertThat(QuizValidationUtils.isAnswerExplanationInternallyInconsistent(
                choices, 2, "MCQ", explanation, null)).isTrue();
        assertThat(QuizValidationUtils.isAnswerExplanationInternallyInconsistent(
                choices, 1, "MCQ", explanation, null)).isFalse();
    }

    @Test
    void answerExplanationConsistency_toleratesLatexDelimiterMismatch() {
        assertThat(QuizValidationUtils.isAnswerExplanationInternallyInconsistent(
                List.of("15\\%", "30\\%", "25\\%", "10\\%"), 1, "MCQ",
                "The result is $30\\%$.", null)).isFalse();
    }

    @Test
    void answerExplanationConsistency_toleratesTextWrapperAndTildeSpacing() {
        assertThat(QuizValidationUtils.isAnswerExplanationInternallyInconsistent(
                List.of("5 mL", "10 \\text{ mL}", "15 mL", "20 mL"), 1, "MCQ",
                "The dose is 10 mL.", null)).isFalse();
        assertThat(QuizValidationUtils.isAnswerExplanationInternallyInconsistent(
                List.of("1732 N", "3464~N", "5196 N", "6928 N"), 1, "MCQ",
                "The force is 3464 N.", null)).isFalse();
    }

    @Test
    void answerExplanationConsistency_toleratesChoicePrecisionRounding() {
        assertThat(QuizValidationUtils.isAnswerExplanationInternallyInconsistent(
                List.of("₱10,000", "₱12,000", "₱13,393", "₱15,000"), 2, "MCQ",
                "The computed amount is ₱13,392.86.", null)).isFalse();
    }

    @Test
    void answerExplanationConsistency_usesNumericBoundariesForOverlappingChoices() {
        List<String> choices = List.of("5%", "25%", "35%", "45%");

        assertThat(QuizValidationUtils.isAnswerExplanationInternallyInconsistent(
                choices, 1, "MCQ", "The result is 25%.", null)).isFalse();
        assertThat(QuizValidationUtils.isAnswerExplanationInternallyInconsistent(
                choices, 0, "MCQ", "The result is 25%.", null)).isTrue();
    }

    @Test
    void answerExplanationConsistency_acceptsKeyWhenExplanationAlsoMentionsDistractor() {
        assertThat(QuizValidationUtils.isAnswerExplanationInternallyInconsistent(
                List.of("23%", "30%", "15%", "50%"), 1, "MCQ",
                "A common error is 15/65 ≈ 23%, but the correct base gives 30%.", null)).isFalse();
    }

    @Test
    void answerExplanationConsistency_skipsProseChoicesAndNonMcqFormats() {
        assertThat(QuizValidationUtils.isAnswerExplanationInternallyInconsistent(
                List.of("Faster", "Slower", "Unchanged", "Unknown"), 0, "MCQ",
                "The explanation names Slower.", null)).isFalse();

        for (String format : List.of(
                "TRUE_FALSE", "MULTI_SELECT", "MATCHING", "IDENTIFICATION", "ENUMERATION")) {
            assertThat(QuizValidationUtils.isAnswerExplanationInternallyInconsistent(
                    List.of("10%", "20%", "30%", "40%"), 0, format,
                    "The result is 20%.", null)).as(format).isFalse();
        }
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
