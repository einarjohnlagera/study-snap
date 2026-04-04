package com.studysnap.backend.util;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@UtilityClass
public class QuizValidationUtils {

    public boolean hasInvalidChoices(List<String> choices) {
        if (choices == null || choices.size() != 4) {
            return true;
        }
        Set<String> normalizedChoices = new HashSet<>();
        for (String choice : choices) {
            if (StringNormalizationUtils.isBlank(choice)) {
                return true;
            }
            if (!normalizedChoices.add(StringNormalizationUtils.normalizeForChoiceDuplicateCheck(choice))) {
                return true;
            }
        }
        return false;
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
