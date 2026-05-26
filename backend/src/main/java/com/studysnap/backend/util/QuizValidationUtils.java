package com.studysnap.backend.util;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

@UtilityClass
public class QuizValidationUtils {
    private static final int MCQ_CHOICE_COUNT = 4;
    private static final int TRUE_FALSE_CHOICE_COUNT = 2;
    private static final String TRUE_FALSE_FORMAT = "TRUE_FALSE";
    private static final Pattern LEADING_CHOICE_LABEL_PATTERN = Pattern.compile("^\\s*[A-Da-d]\\s*[.)]\\s*");

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
