package com.studysnap.backend.util;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

@UtilityClass
public class QuizValidationUtils {
    private static final int MCQ_CHOICE_COUNT = 4;
    private static final int TRUE_FALSE_CHOICE_COUNT = 2;
    private static final String TRUE_FALSE_FORMAT = "TRUE_FALSE";
    private static final String MULTI_SELECT_FORMAT = "MULTI_SELECT";
    private static final String TRUE_CHOICE = "true";
    private static final String FALSE_CHOICE = "false";
    private static final int MIN_MULTI_SELECT_CORRECT_INDICES = 2;
    private static final int MAX_MULTI_SELECT_CORRECT_INDICES = 3;
    private static final Pattern LEADING_CHOICE_LABEL_PATTERN = Pattern.compile("^\\s*[A-Da-d]\\s*[.)]\\s*");
    private static final Pattern WHICH_CHOICE_STEM_PATTERN = Pattern.compile("which\\s+(is|are|of the following|statement|one|of these)");
    private static final Pattern STATEMENT_ONE_PATTERN = Pattern.compile("statement\\s*1");
    private static final Pattern STATEMENT_TWO_PATTERN = Pattern.compile("statement\\s*2");
    private static final Pattern ALL_EXCEPT_PATTERN = Pattern.compile("all of the following.{0,40}except");

    public boolean hasInvalidChoices(List<String> choices, String questionFormat) {
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
        if (!isEffectivelyTrueFalse(choices, questionFormat)) {
            return false;
        }
        String normalizedQuestion = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(question);
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
        if (choice == null) {
            return null;
        }
        return StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(
                LEADING_CHOICE_LABEL_PATTERN.matcher(choice).replaceFirst("")
        );
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

    public List<String> randomizeChoices(List<String> choices, String question) {
        List<String> shuffled = new ArrayList<>(choices);
        long seed = StringNormalizationUtils.normalizeForDuplicateCheck(question).hashCode();
        Collections.shuffle(shuffled, new Random(seed));
        return shuffled;
    }

    public String buildFallbackExplanation(String concept) {
        if (StringNormalizationUtils.isBlank(concept)) {
            return "Review this question in your notes.";
        }
        return "Review the " + concept + " concept in your notes.";
    }
}
