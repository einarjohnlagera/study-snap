package com.studysnap.backend.util;

import com.studysnap.backend.dto.ChallengeQuizConceptStatResponse;
import com.studysnap.backend.dto.QuizItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class QuizSessionReviewUtils {
    public static final int WEAK_CONCEPT_THRESHOLD = 60;
    private static final String MULTI_SELECT_FORMAT = "MULTI_SELECT";
    private static final String IDENTIFICATION_FORMAT = "IDENTIFICATION";
    private static final String UNKNOWN_CONCEPT_LABEL = "Unknown";

    private QuizSessionReviewUtils() {}

    public static List<ChallengeQuizConceptStatResponse> computeConceptBreakdown(
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices
    ) {
        return computeConceptBreakdown(quiz, selectedChoices, Map.of());
    }

    public static List<ChallengeQuizConceptStatResponse> computeConceptBreakdown(
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices
    ) {
        return computeConceptBreakdown(quiz, selectedChoices, selectedMultiChoices, Map.of());
    }

    public static List<ChallengeQuizConceptStatResponse> computeConceptBreakdown(
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices,
            Map<Integer, String> selectedIdentificationAnswers
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
            if (isAnswerCorrect(item, index, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers)) {
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

    public static List<String> computeFullyCorrectConcepts(List<ChallengeQuizConceptStatResponse> conceptBreakdown) {
        if (conceptBreakdown == null || conceptBreakdown.isEmpty()) {
            return List.of();
        }
        return conceptBreakdown.stream()
                .filter(stat -> stat != null && stat.totalQuestions() > 0)
                .filter(stat -> stat.correctAnswers() == stat.totalQuestions())
                .map(ChallengeQuizConceptStatResponse::concept)
                .toList();
    }

    public static List<String> computeConceptsWithMisses(List<ChallengeQuizConceptStatResponse> conceptBreakdown) {
        if (conceptBreakdown == null || conceptBreakdown.isEmpty()) {
            return List.of();
        }
        return conceptBreakdown.stream()
                .filter(stat -> stat != null && stat.totalQuestions() > 0)
                .filter(stat -> stat.correctAnswers() < stat.totalQuestions())
                .map(ChallengeQuizConceptStatResponse::concept)
                .toList();
    }

    public static List<String> computeFullyCorrectKeyConcepts(
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices
    ) {
        return computeFullyCorrectKeyConcepts(quiz, selectedChoices, selectedMultiChoices, Map.of());
    }

    public static List<String> computeFullyCorrectKeyConcepts(
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices,
            Map<Integer, String> selectedIdentificationAnswers
    ) {
        if (quiz == null || quiz.isEmpty()) {
            return List.of();
        }

        Map<String, ConceptCounter> counters = computeEffectiveKeyConceptCounters(
                quiz,
                selectedChoices,
                selectedMultiChoices,
                selectedIdentificationAnswers
        );
        return counters.entrySet().stream()
                .filter(entry -> entry.getValue().totalQuestions > 0)
                .filter(entry -> entry.getValue().correctAnswers == entry.getValue().totalQuestions)
                .map(Map.Entry::getKey)
                .toList();
    }

    public static List<String> computeKeyConceptsWithMisses(
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices
    ) {
        return computeKeyConceptsWithMisses(quiz, selectedChoices, selectedMultiChoices, Map.of());
    }

    public static List<String> computeKeyConceptsWithMisses(
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices,
            Map<Integer, String> selectedIdentificationAnswers
    ) {
        if (quiz == null || quiz.isEmpty()) {
            return List.of();
        }

        Map<String, ConceptCounter> counters = computeEffectiveKeyConceptCounters(
                quiz,
                selectedChoices,
                selectedMultiChoices,
                selectedIdentificationAnswers
        );
        return counters.entrySet().stream()
                .filter(entry -> entry.getValue().totalQuestions > 0)
                .filter(entry -> entry.getValue().correctAnswers < entry.getValue().totalQuestions)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static Map<String, ConceptCounter> computeEffectiveKeyConceptCounters(
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices,
            Map<Integer, String> selectedIdentificationAnswers
    ) {
        Map<String, ConceptCounter> counters = new LinkedHashMap<>();
        for (int index = 0; index < quiz.size(); index++) {
            QuizItem item = quiz.get(index);
            if (item == null) {
                continue;
            }
            String concept = normalizeConcept(effectiveKeyConcept(item));
            ConceptCounter counter = counters.computeIfAbsent(concept, ignored -> new ConceptCounter());
            counter.totalQuestions += 1;
            if (isAnswerCorrect(item, index, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers)) {
                counter.correctAnswers += 1;
            }
        }
        return counters;
    }

    private static String effectiveKeyConcept(QuizItem item) {
        if (item.keyConcept() != null && !item.keyConcept().isBlank()) {
            return item.keyConcept();
        }
        return item.concept();
    }

    private static String normalizeConcept(String concept) {
        if (concept == null) {
            return UNKNOWN_CONCEPT_LABEL;
        }
        String trimmed = concept.trim();
        return trimmed.isEmpty() ? UNKNOWN_CONCEPT_LABEL : trimmed;
    }

    public static boolean isAnswerCorrect(
            QuizItem item,
            int questionIndex,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices
    ) {
        return isAnswerCorrect(item, questionIndex, selectedChoices, selectedMultiChoices, Map.of());
    }

    public static boolean isAnswerCorrect(
            QuizItem item,
            int questionIndex,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices,
            Map<Integer, String> selectedIdentificationAnswers
    ) {
        if (item == null) {
            return false;
        }
        if (IDENTIFICATION_FORMAT.equals(item.questionFormat())) {
            String selected = selectedIdentificationAnswers == null ? null : selectedIdentificationAnswers.get(questionIndex);
            String normalizedSelected = normalizeIdentificationAnswer(selected);
            if (normalizedSelected == null || item.acceptableAnswers() == null || item.acceptableAnswers().isEmpty()) {
                return false;
            }
            return item.acceptableAnswers().stream()
                    .map(QuizSessionReviewUtils::normalizeIdentificationAnswer)
                    .anyMatch(normalizedSelected::equals);
        }
        if (MULTI_SELECT_FORMAT.equals(item.questionFormat())) {
            List<Integer> selected = selectedMultiChoices == null ? List.of() : selectedMultiChoices.getOrDefault(questionIndex, List.of());
            List<Integer> correct = item.correctIndices() == null ? List.of() : item.correctIndices();
            return !selected.isEmpty() && selected.size() == correct.size()
                    && selected.stream().sorted().toList().equals(correct.stream().sorted().toList());
        }
        Integer selectedChoiceIndex = selectedChoices == null ? null : selectedChoices.get(questionIndex);
        return selectedChoiceIndex != null && selectedChoiceIndex.equals(item.correctIndex());
    }

    private static String normalizeIdentificationAnswer(String answer) {
        if (answer == null) {
            return null;
        }
        String normalized = answer.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private static final class ConceptCounter {
        private int correctAnswers;
        private int totalQuestions;
    }
}
