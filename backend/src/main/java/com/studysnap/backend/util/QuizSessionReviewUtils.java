package com.studysnap.backend.util;

import com.studysnap.backend.dto.ChallengeQuizConceptStatResponse;
import com.studysnap.backend.dto.QuizItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QuizSessionReviewUtils {
    public static final int WEAK_CONCEPT_THRESHOLD = 60;
    private static final String UNKNOWN_CONCEPT_LABEL = "Unknown";

    private QuizSessionReviewUtils() {}

    public static List<ChallengeQuizConceptStatResponse> computeConceptBreakdown(
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices
    ) {
        if (quiz == null || quiz.isEmpty()) {
            return List.of();
        }

        Map<String, ConceptCounter> counters = new LinkedHashMap<>();
        for (int index = 0; index < quiz.size(); index++) {
            QuizItem item = quiz.get(index);
            if (item == null) {
                continue;
            }
            String concept = normalizeConcept(item.concept());
            ConceptCounter counter = counters.computeIfAbsent(concept, ignored -> new ConceptCounter());
            counter.totalQuestions += 1;
            Integer selectedChoiceIndex = selectedChoices == null ? null : selectedChoices.get(index);
            if (selectedChoiceIndex != null && selectedChoiceIndex.equals(item.correctIndex())) {
                counter.correctAnswers += 1;
            }
        }

        return counters.entrySet().stream()
                .map(entry -> {
                    int totalQuestions = entry.getValue().totalQuestions;
                    int correctAnswers = entry.getValue().correctAnswers;
                    int accuracyPercentage = totalQuestions > 0
                            ? (int) Math.round((correctAnswers * 100.0) / totalQuestions)
                            : 0;
                    return new ChallengeQuizConceptStatResponse(
                            entry.getKey(),
                            correctAnswers,
                            totalQuestions,
                            accuracyPercentage
                    );
                })
                .toList();
    }

    public static List<String> computeWeakConcepts(List<ChallengeQuizConceptStatResponse> conceptBreakdown) {
        if (conceptBreakdown == null || conceptBreakdown.isEmpty()) {
            return List.of();
        }
        return conceptBreakdown.stream()
                .filter(stat -> stat.accuracyPercentage() < WEAK_CONCEPT_THRESHOLD)
                .map(ChallengeQuizConceptStatResponse::concept)
                .toList();
    }

    private static String normalizeConcept(String concept) {
        if (concept == null) {
            return UNKNOWN_CONCEPT_LABEL;
        }
        String trimmed = concept.trim();
        return trimmed.isEmpty() ? UNKNOWN_CONCEPT_LABEL : trimmed;
    }

    private static final class ConceptCounter {
        private int correctAnswers;
        private int totalQuestions;
    }
}
