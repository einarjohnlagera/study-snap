package com.studysnap.backend.util;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class QuizValidationUtils {
    private static final int MCQ_CHOICE_COUNT = 4;
    private static final int TRUE_FALSE_CHOICE_COUNT = 2;
    private static final String TRUE_FALSE_FORMAT = "TRUE_FALSE";
    private static final String MULTI_SELECT_FORMAT = "MULTI_SELECT";
    private static final String IDENTIFICATION_FORMAT = "IDENTIFICATION";
    private static final String ENUMERATION_FORMAT = "ENUMERATION";
    private static final String MCQ_FORMAT = "MCQ";
    private static final String TRUE_CHOICE = "true";
    private static final String FALSE_CHOICE = "false";
    private static final int MIN_MULTI_SELECT_CORRECT_INDICES = 2;
    private static final int MAX_MULTI_SELECT_CORRECT_INDICES = 3;
    private static final Pattern LEADING_CHOICE_LABEL_PATTERN = Pattern.compile("^\\s*[A-Da-d]\\s*[.)]\\s*");
    private static final Pattern WHICH_CHOICE_STEM_PATTERN = Pattern.compile("which\\s+(is|are|of the following|statement|one|of these)");
    private static final Pattern STATEMENT_ONE_PATTERN = Pattern.compile("statement\\s*1");
    private static final Pattern STATEMENT_TWO_PATTERN = Pattern.compile("statement\\s*2");
    private static final Pattern ALL_EXCEPT_PATTERN = Pattern.compile("all of the following.{0,40}except");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d");
    private static final Pattern LATEX_TEXT_WRAPPER_PATTERN = Pattern.compile("\\\\text\\s*\\{([^{}]*)}");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    /**
     * An IDENTIFICATION answer is graded by exact normalized string equality, so it must be a term, name or
     * label a learner can type unambiguously in words. A stem asking for the *notation itself* — an
     * expression, formula, equation — cannot be graded that way: {@code x^2 + y^2}, {@code x² + y²} and
     * {@code y² + x²} are all correct and none of them string-match each other.
     *
     * <p>Observed in production data: "Identify the algebraic expression for the sum of the squares of two
     * variables $x$ and $y$." shipped as IDENTIFICATION with acceptableAnswers
     * {@code ["sum of squares", "x squared plus y squared", ...]} — so the expression the stem explicitly
     * asked for was marked WRONG, while echoing the stem back was marked right. Two independent generations
     * produced it, so this is reproducible model behaviour rather than one bad sample.
     *
     * <p>The prompt already permitted a formula's *name* ("formula name") and the model read that as the
     * formula. This guard is deliberately about the ANSWER'S FORM, not the subject, so it covers chemical
     * formulas and code as well as maths. The prompt rule is the primary fix; this catches the case when the
     * model ignores it.
     */
    private static final Pattern NOTATION_ANSWER_STEM_PATTERN = Pattern.compile(
            "(identify|give|write|state|provide|what is)\\b.{0,40}\\b(algebraic|mathematical|chemical)?\\s*"
            + "(expression|formula|equation|notation)\\b"
    );

    public boolean hasInvalidChoices(List<String> choices, String questionFormat) {
        if (IDENTIFICATION_FORMAT.equals(questionFormat) || ENUMERATION_FORMAT.equals(questionFormat)) {
            return choices != null && !choices.isEmpty();
        }
        int expectedChoiceCount = TRUE_FALSE_FORMAT.equals(questionFormat)
                ? TRUE_FALSE_CHOICE_COUNT
                : MCQ_CHOICE_COUNT;
        if (choices == null || choices.size() != expectedChoiceCount) {
            return true;
        }
        Set<String> normalizedChoices = new HashSet<>();
        for (String choice : choices) {
            String sanitizedChoice = sanitizeChoiceText(choice);
            if (StringNormalizationUtils.isBlank(sanitizedChoice)) {
                return true;
            }
            if (!normalizedChoices.add(StringNormalizationUtils.normalizeForChoiceDuplicateCheck(sanitizedChoice))) {
                return true;
            }
        }
        return false;
    }

    public boolean hasInvalidCorrectIndices(List<Integer> correctIndices, List<String> choices, String questionFormat) {
        if (!MULTI_SELECT_FORMAT.equals(questionFormat)) {
            return false;
        }
        if (choices == null || choices.size() != MCQ_CHOICE_COUNT
                || correctIndices == null
                || correctIndices.size() < MIN_MULTI_SELECT_CORRECT_INDICES
                || correctIndices.size() > MAX_MULTI_SELECT_CORRECT_INDICES) {
            return true;
        }
        Set<Integer> uniqueIndices = new HashSet<>();
        for (Integer index : correctIndices) {
            if (index == null || index < 0 || index >= choices.size() || !uniqueIndices.add(index)) {
                return true;
            }
        }
        return false;
    }

    public boolean isFormatStemMismatch(String question, List<String> choices, String questionFormat) {
        String normalizedStem = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(question);
        if (IDENTIFICATION_FORMAT.equals(questionFormat)) {
            return normalizedStem != null
                    && NOTATION_ANSWER_STEM_PATTERN.matcher(normalizedStem.toLowerCase(Locale.ROOT)).find();
        }
        if (!isEffectivelyTrueFalse(choices, questionFormat)) {
            return false;
        }
        String normalizedQuestion = normalizedStem;
        if (normalizedQuestion == null) {
            return false;
        }
        String lowerQuestion = normalizedQuestion.toLowerCase(Locale.ROOT);
        return WHICH_CHOICE_STEM_PATTERN.matcher(lowerQuestion).find()
                || (STATEMENT_ONE_PATTERN.matcher(lowerQuestion).find()
                && STATEMENT_TWO_PATTERN.matcher(lowerQuestion).find())
                || ALL_EXCEPT_PATTERN.matcher(lowerQuestion).find();
    }

    public List<String> sanitizeChoiceTexts(List<String> choices) {
        if (choices == null || choices.isEmpty()) {
            return List.of();
        }
        return choices.stream()
                .map(QuizValidationUtils::sanitizeChoiceText)
                .map(choice -> choice == null ? "" : choice)
                .toList();
    }

    public String sanitizeChoiceText(String choice) {
        return sanitizeChoiceText(choice, true);
    }

    /**
     * ⚠️ This method does TWO things, and only ONE of them is unsafe to repeat.
     *
     * <p>{@code LEADING_CHOICE_LABEL_PATTERN.replaceFirst} is NOT idempotent — repeating it eats a second
     * token ({@code "A. B. Smith"} → {@code "B. Smith"} → {@code "Smith"}). Whitespace normalization IS
     * idempotent and must keep running on every path, or a stored {@code "Ohm's   Law"} stops matching a
     * legacy {@code "Ohm's Law"} answer and the question loses its correct index entirely.
     */
    public String sanitizeChoiceText(String choice, boolean stripLeadingLabel) {
        if (choice == null) {
            return null;
        }
        String withoutLabel = stripLeadingLabel
                ? LEADING_CHOICE_LABEL_PATTERN.matcher(choice).replaceFirst("")
                : choice;
        return StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(withoutLabel);
    }

    /** Whitespace-only normalization, for text whose label was already stripped when it was generated. */
    public List<String> normalizeChoiceTexts(List<String> choices) {
        if (choices == null) {
            return List.of();
        }
        return choices.stream()
                .map(choice -> sanitizeChoiceText(choice, false))
                .map(choice -> choice == null ? "" : choice)
                .toList();
    }

    private boolean isEffectivelyTrueFalse(List<String> choices, String questionFormat) {
        if (TRUE_FALSE_FORMAT.equalsIgnoreCase(questionFormat)) {
            return true;
        }
        if (choices == null || choices.size() != TRUE_FALSE_CHOICE_COUNT) {
            return false;
        }
        Set<String> normalizedChoices = new HashSet<>();
        for (String choice : choices) {
            String sanitizedChoice = sanitizeChoiceText(choice);
            if (sanitizedChoice == null) {
                return false;
            }
            normalizedChoices.add(sanitizedChoice.toLowerCase(Locale.ROOT));
        }
        return normalizedChoices.size() == TRUE_FALSE_CHOICE_COUNT
                && normalizedChoices.contains(TRUE_CHOICE)
                && normalizedChoices.contains(FALSE_CHOICE);
    }

    /**
     * Detects the narrow answer/explanation contradiction measured in the v0.155.0 incident audit.
     * A false result means only that this check found no contradiction; it is not semantic verification.
     */
    public boolean isAnswerExplanationInternallyInconsistent(
            List<String> choices,
            Integer correctIndex,
            String questionFormat,
            String explanation,
            String workingSolution
    ) {
        if (questionFormat != null && !MCQ_FORMAT.equals(questionFormat)) {
            return false;
        }
        if (choices == null || choices.isEmpty() || correctIndex == null
                || correctIndex < 0 || correctIndex >= choices.size()
                || choices.stream().anyMatch(choice -> !isNumericUnitLiteral(choice))) {
            return false;
        }

        String evidence = normalizeConsistencyText(
                (explanation == null ? "" : explanation) + " "
                        + (workingSolution == null ? "" : workingSolution)
        );
        if (choiceOccursInEvidence(choices.get(correctIndex), evidence)) {
            return false;
        }
        for (int index = 0; index < choices.size(); index++) {
            if (index != correctIndex && choiceOccursInEvidence(choices.get(index), evidence)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNumericUnitLiteral(String choice) {
        return choice != null && choice.length() <= 20 && DIGIT_PATTERN.matcher(choice).find();
    }

    private boolean choiceOccursInEvidence(String choice, String normalizedEvidence) {
        String normalizedChoice = normalizeConsistencyText(choice);
        if (normalizedChoice.isEmpty()) {
            return false;
        }
        Pattern exactChoice = Pattern.compile(
                "(?<![\\d.])" + Pattern.quote(normalizedChoice) + "(?!\\d|\\.\\d)"
        );
        if (exactChoice.matcher(normalizedEvidence).find()) {
            return true;
        }
        return roundedChoiceOccursInEvidence(normalizedChoice, normalizedEvidence);
    }

    private boolean roundedChoiceOccursInEvidence(String normalizedChoice, String normalizedEvidence) {
        Matcher choiceNumberMatcher = NUMBER_PATTERN.matcher(normalizedChoice);
        if (!choiceNumberMatcher.find()) {
            return false;
        }
        String choiceNumberText = choiceNumberMatcher.group();
        int choiceNumberStart = choiceNumberMatcher.start();
        int choiceNumberEnd = choiceNumberMatcher.end();
        if (choiceNumberMatcher.find()) {
            return false;
        }

        String prefix = normalizedChoice.substring(0, choiceNumberStart);
        String suffix = normalizedChoice.substring(choiceNumberEnd);
        Pattern roundedCandidatePattern = Pattern.compile(
                "(?<![\\d.])" + Pattern.quote(prefix)
                        + "([-+]?\\d+(?:\\.\\d+)?)"
                        + Pattern.quote(suffix) + "(?!\\d|\\.\\d)"
        );
        BigDecimal choiceNumber = new BigDecimal(choiceNumberText);
        int choicePrecision = Math.max(0, choiceNumber.scale());
        Matcher candidateMatcher = roundedCandidatePattern.matcher(normalizedEvidence);
        while (candidateMatcher.find()) {
            BigDecimal candidate = new BigDecimal(candidateMatcher.group(1));
            if (candidate.setScale(choicePrecision, RoundingMode.HALF_UP).compareTo(choiceNumber) == 0) {
                return true;
            }
        }
        return false;
    }

    private String normalizeConsistencyText(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        Matcher textWrapperMatcher = LATEX_TEXT_WRAPPER_PATTERN.matcher(normalized);
        while (textWrapperMatcher.find()) {
            normalized = textWrapperMatcher.replaceAll("$1");
            textWrapperMatcher = LATEX_TEXT_WRAPPER_PATTERN.matcher(normalized);
        }
        normalized = normalized
                .replace("\\(", "")
                .replace("\\)", "")
                .replace("\\%", "%")
                .replace('$', ' ')
                .replace(",", "")
                .replace('~', ' ');
        return WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
    }

    public String buildFallbackExplanation(String concept) {
        if (StringNormalizationUtils.isBlank(concept)) {
            return "Review this question in your notes.";
        }
        return "Review the " + concept + " concept in your notes.";
    }
}
