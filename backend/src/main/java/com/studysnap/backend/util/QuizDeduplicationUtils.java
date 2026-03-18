package com.studysnap.backend.util;

import com.studysnap.backend.dto.QuizItem;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@UtilityClass
public class QuizDeduplicationUtils {

    public Set<String> toNormalizedQuestionSet(List<QuizItem> quizItems) {
        Set<String> normalized = new LinkedHashSet<>();
        if (quizItems == null || quizItems.isEmpty()) {
            return normalized;
        }

        for (QuizItem item : quizItems) {
            if (item == null) {
                continue;
            }
            String normalizedQuestion = normalizeQuestion(item.question());
            if (!normalizedQuestion.isBlank()) {
                normalized.add(normalizedQuestion);
            }
        }
        return normalized;
    }

    public Set<String> toNormalizedQuestionSetFromStrings(List<String> questions) {
        Set<String> normalized = new LinkedHashSet<>();
        if (questions == null || questions.isEmpty()) {
            return normalized;
        }

        for (String question : questions) {
            String normalizedQuestion = normalizeQuestion(question);
            if (!normalizedQuestion.isBlank()) {
                normalized.add(normalizedQuestion);
            }
        }
        return normalized;
    }

    public List<QuizItem> uniqueQuestions(List<QuizItem> quizItems, Set<String> disallowedNormalizedQuestions) {
        if (quizItems == null || quizItems.isEmpty()) {
            return List.of();
        }

        Set<String> seen = new LinkedHashSet<>();
        if (disallowedNormalizedQuestions != null && !disallowedNormalizedQuestions.isEmpty()) {
            seen.addAll(disallowedNormalizedQuestions);
        }

        List<QuizItem> unique = new ArrayList<>();
        for (QuizItem item : quizItems) {
            if (item == null) {
                continue;
            }
            String normalizedQuestion = normalizeQuestion(item.question());
            if (normalizedQuestion.isBlank() || !seen.add(normalizedQuestion)) {
                continue;
            }
            unique.add(item);
        }
        return unique;
    }

    public String normalizeQuestion(String question) {
        return StringNormalizationUtils.normalizeForDuplicateCheck(question);
    }
}
