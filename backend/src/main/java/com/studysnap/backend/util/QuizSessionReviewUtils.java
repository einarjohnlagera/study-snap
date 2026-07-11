package com.studysnap.backend.util;

import com.studysnap.backend.dto.ChallengeQuizConceptStatResponse;
import com.studysnap.backend.dto.QuizItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class QuizSessionReviewUtils {
    public static final int WEAK_CONCEPT_THRESHOLD = 60;
    private static final String MULTI_SELECT_FORMAT = "MULTI_SELECT";
    private static final String IDENTIFICATION_FORMAT = "IDENTIFICATION";
    private static final String ENUMERATION_FORMAT = "ENUMERATION";
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

    /**
     * Derives concept results only when both the quiz and at least one persisted answer selection exist.
     */
    public static List<ChallengeQuizConceptStatResponse> computeConceptBreakdownForStoredSelections(
            List<QuizItem> quiz,
            Map<String, Object> sessionState
    ) {
        if (quiz == null || quiz.isEmpty()) {
            return List.of();
        }

        Map<Integer, Integer> selectedChoices = QuizSessionStateUtils.extractSelectedChoiceIndexes(sessionState, quiz);
        Map<Integer, List<Integer>> selectedMultiChoices =
                QuizSessionStateUtils.extractSelectedMultiChoiceIndexes(sessionState, quiz);
        if (selectedChoices.isEmpty() && selectedMultiChoices.isEmpty()) {
            return List.of();
        }
        return computeConceptBreakdown(quiz, selectedChoices, selectedMultiChoices);
    }

    /**
     * Derives concept results from a quiz snapshot and its persisted selections.
     */
    public static List<ChallengeQuizConceptStatResponse> computeConceptBreakdownForStoredSelections(
            Map<String, Object> sessionState
    ) {
        return computeConceptBreakdownForStoredSelections(QuizSessionStateUtils.extractQuiz(sessionState), sessionState);
    }

    public static List<ChallengeQuizConceptStatResponse> computeConceptBreakdown(
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices,
            Map<Integer, String> selectedIdentificationAnswers
    ) {
        return computeConceptBreakdown(quiz, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers, Map.of());
    }

    public static List<ChallengeQuizConceptStatResponse> computeConceptBreakdown(
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices,
            Map<Integer, String> selectedIdentificationAnswers,
            Map<Integer, List<String>> selectedEnumerationAnswers
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
            if (isAnswerCorrect(item, index, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers, selectedEnumerationAnswers)) {
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
        return computeFullyCorrectKeyConcepts(quiz, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers, Map.of());
    }

    public static List<String> computeFullyCorrectKeyConcepts(
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices,
            Map<Integer, String> selectedIdentificationAnswers,
            Map<Integer, List<String>> selectedEnumerationAnswers
    ) {
        if (quiz == null || quiz.isEmpty()) {
            return List.of();
        }

        Map<String, ConceptCounter> counters = computeEffectiveKeyConceptCounters(
                quiz,
                selectedChoices,
                selectedMultiChoices,
                selectedIdentificationAnswers,
                selectedEnumerationAnswers
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
        return computeKeyConceptsWithMisses(quiz, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers, Map.of());
    }

    public static List<String> computeKeyConceptsWithMisses(
            List<QuizItem> quiz,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices,
            Map<Integer, String> selectedIdentificationAnswers,
            Map<Integer, List<String>> selectedEnumerationAnswers
    ) {
        if (quiz == null || quiz.isEmpty()) {
            return List.of();
        }

        Map<String, ConceptCounter> counters = computeEffectiveKeyConceptCounters(
                quiz,
                selectedChoices,
                selectedMultiChoices,
                selectedIdentificationAnswers,
                selectedEnumerationAnswers
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
            Map<Integer, String> selectedIdentificationAnswers,
            Map<Integer, List<String>> selectedEnumerationAnswers
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
            if (isAnswerCorrect(item, index, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers, selectedEnumerationAnswers)) {
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
        return isAnswerCorrect(item, questionIndex, selectedChoices, selectedMultiChoices, selectedIdentificationAnswers, Map.of());
    }

    public static boolean isAnswerCorrect(
            QuizItem item,
            int questionIndex,
            Map<Integer, Integer> selectedChoices,
            Map<Integer, List<Integer>> selectedMultiChoices,
            Map<Integer, String> selectedIdentificationAnswers,
            Map<Integer, List<String>> selectedEnumerationAnswers
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
        if (ENUMERATION_FORMAT.equals(item.questionFormat())) {
            return isEnumerationAnswerCorrect(item, questionIndex, selectedEnumerationAnswers);
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

    private static boolean isEnumerationAnswerCorrect(
            QuizItem item,
            int questionIndex,
            Map<Integer, List<String>> selectedEnumerationAnswers
    ) {
        List<List<String>> groups = item.acceptableAnswerGroups();
        if (groups == null || groups.isEmpty()) {
            return false;
        }
        List<String> rawSubmitted = selectedEnumerationAnswers == null
                ? List.of()
                : selectedEnumerationAnswers.getOrDefault(questionIndex, List.of());
        List<String> submitted = rawSubmitted.stream()
                .map(QuizSessionReviewUtils::normalizeIdentificationAnswer)
                .filter(Objects::nonNull)
                .toList();
        if (submitted.size() != groups.size()) {
            return false;
        }
        return hasCompleteEnumerationAssignment(submitted, groups, new boolean[groups.size()], 0);
    }

    private static boolean hasCompleteEnumerationAssignment(
            List<String> submitted,
            List<List<String>> groups,
            boolean[] usedGroups,
            int submittedIndex
    ) {
        if (submittedIndex == submitted.size()) {
            return true;
        }
        String normalizedSubmittedAnswer = submitted.get(submittedIndex);
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            if (usedGroups[groupIndex] || !enumerationGroupMatches(groups.get(groupIndex), normalizedSubmittedAnswer)) {
                continue;
            }
            usedGroups[groupIndex] = true;
            if (hasCompleteEnumerationAssignment(submitted, groups, usedGroups, submittedIndex + 1)) {
                return true;
            }
            usedGroups[groupIndex] = false;
        }
        return false;
    }

    private static boolean enumerationGroupMatches(List<String> group, String normalizedSubmittedAnswer) {
        return group != null && group.stream()
                .map(QuizSessionReviewUtils::normalizeIdentificationAnswer)
                .anyMatch(normalizedSubmittedAnswer::equals);
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
