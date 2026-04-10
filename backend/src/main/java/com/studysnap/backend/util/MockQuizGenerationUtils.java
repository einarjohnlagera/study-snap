package com.studysnap.backend.util;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@UtilityClass
public class MockQuizGenerationUtils {
    private static final String DEFAULT_TOPIC_LABEL = "your notes";
    private static final int MAX_TOPIC_LABEL_LENGTH = 48;
    private static final int CHALLENGE_TEMPLATE_COUNT = 15;
    private static final int ADAPTIVE_TEMPLATE_COUNT = 10;
    private static final int MAX_GENERATION_ATTEMPTS_MULTIPLIER = 20;
    private static final List<String> FALLBACK_CONCEPTS = List.of(
            "Core Principle",
            "Key Relationship",
            "Applied Example",
            "Common Misconception"
    );

    public List<QuizItem> generateChallengeQuiz(
            String studyPackTitle,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            String difficulty,
            StudyPackGenerationContext context
    ) {
        List<String> concepts = resolveConcepts(keyConcepts);
        String topic = resolveTopicLabel(studyPackTitle, context);
        String difficultyFocus = resolveDifficultyFocus(difficulty);
        return buildUniqueQuizItems(questionCount, disallowedQuestions, attempt ->
                buildChallengeQuizItem(attempt, concepts, topic, difficultyFocus)
        );
    }

    public List<QuizItem> generateAdaptivePracticeQuiz(
            String studyPackTitle,
            List<String> weakConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            StudyPackGenerationContext context
    ) {
        List<String> concepts = resolveConcepts(weakConcepts);
        String topic = resolveTopicLabel(studyPackTitle, context);
        return buildUniqueQuizItems(questionCount, disallowedQuestions, attempt ->
                buildAdaptiveQuizItem(attempt, concepts, topic)
        );
    }

    private List<QuizItem> buildUniqueQuizItems(
            int questionCount,
            List<String> disallowedQuestions,
            IndexedQuizItemBuilder builder
    ) {
        int normalizedCount = Math.max(1, questionCount);
        Set<String> seenQuestions = new LinkedHashSet<>(QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(disallowedQuestions));
        List<QuizItem> quizItems = new ArrayList<>(normalizedCount);
        int attempt = 0;
        int maxAttempts = normalizedCount * MAX_GENERATION_ATTEMPTS_MULTIPLIER;
        while (quizItems.size() < normalizedCount && attempt < maxAttempts) {
            QuizItem item = builder.build(attempt);
            attempt++;
            String normalizedQuestion = QuizDeduplicationUtils.normalizeQuestion(item.question());
            if (normalizedQuestion.isBlank() || !seenQuestions.add(normalizedQuestion)) {
                continue;
            }
            quizItems.add(item);
        }
        return quizItems;
    }

    private QuizItem buildChallengeQuizItem(int index, List<String> concepts, String topic, String difficultyFocus) {
        String concept = concepts.get(index % concepts.size());
        int correctIndex = index % 4;
        String question = switch (index % CHALLENGE_TEMPLATE_COUNT) {
            case 0 -> "In " + topic + ", which statement best explains " + concept + "?";
            case 1 -> "Which example best shows correct understanding of " + concept + " from " + topic + "?";
            case 2 -> "What should you identify first when reviewing " + concept + " in " + topic + "?";
            case 3 -> "Which option applies " + concept + " correctly to a new situation from " + topic + "?";
            case 4 -> "Which statement avoids a common misconception about " + concept + " in " + topic + "?";
            case 5 -> "Which choice connects " + concept + " to the main idea of " + topic + "?";
            case 6 -> "When comparing ideas in " + topic + ", what makes " + concept + " distinct?";
            case 7 -> "Which answer reflects the most accurate use of " + concept + " in " + topic + "?";
            case 8 -> "What is the strongest takeaway about " + concept + " from " + topic + "?";
            case 9 -> "Which option shows the most exam-ready understanding of " + concept + " in " + topic + "?";
            case 10 -> "Which statement keeps " + concept + " grounded in the evidence from " + topic + "?";
            case 11 -> "If " + concept + " appears in a question about " + topic + ", which answer is safest?";
            case 12 -> "Which interpretation of " + concept + " best matches the note context in " + topic + "?";
            case 13 -> "Which answer would earn the best reasoning credit for " + concept + " in " + topic + "?";
            default -> "Which statement best prepares you to answer questions about " + concept + " from " + topic + "?";
        };
        if (index >= CHALLENGE_TEMPLATE_COUNT) {
            question = question + " (variation " + (index / CHALLENGE_TEMPLATE_COUNT + 1) + ")";
        }

        String correctChoice = switch (index % 4) {
            case 0 -> "It states the " + difficultyFocus + " of " + concept + " instead of a side detail.";
            case 1 -> "It applies " + concept + " using the strongest evidence from " + topic + ".";
            case 2 -> "It connects " + concept + " to the main relationship emphasized in " + topic + ".";
            default -> "It distinguishes " + concept + " from a nearby but different idea in " + topic + ".";
        };
        List<String> distractors = List.of(
                "It turns " + concept + " into an unsupported absolute claim.",
                "It swaps " + concept + " with an unrelated detail from the notes.",
                "It focuses on memorizing terms without understanding how " + concept + " works."
        );
        String explanation = "The best answer keeps " + concept + " tied to " + difficultyFocus
                + " in " + topic + ", rather than confusing it with side details, absolutes, or rote memorization.";
        return new QuizItem(question, placeChoices(correctChoice, distractors, correctIndex), correctIndex, concept, explanation);
    }

    private QuizItem buildAdaptiveQuizItem(int index, List<String> concepts, String topic) {
        String concept = concepts.get(index % concepts.size());
        int correctIndex = (index + 1) % 4;
        String question = switch (index % ADAPTIVE_TEMPLATE_COUNT) {
            case 0 -> "You previously struggled with " + concept + ". What should you review first from " + topic + "?";
            case 1 -> "Which reminder best strengthens your understanding of " + concept + " in " + topic + "?";
            case 2 -> "When revisiting " + concept + ", which approach is most helpful for " + topic + "?";
            case 3 -> "Which choice directly repairs a weak spot in " + concept + " from " + topic + "?";
            case 4 -> "What is the most useful first correction when you miss a question about " + concept + "?";
            case 5 -> "Which statement rebuilds the foundation of " + concept + " for " + topic + "?";
            case 6 -> "Which answer best helps you stop repeating mistakes about " + concept + "?";
            case 7 -> "If " + concept + " is still weak, what should your next review focus on?";
            case 8 -> "Which explanation would make " + concept + " clearer the next time it appears?";
            default -> "Which option gives the strongest recovery strategy for " + concept + " in " + topic + "?";
        };
        if (index >= ADAPTIVE_TEMPLATE_COUNT) {
            question = question + " (variation " + (index / ADAPTIVE_TEMPLATE_COUNT + 1) + ")";
        }

        String correctChoice = switch (index % 4) {
            case 0 -> "Review the core principle of " + concept + " before moving to edge cases.";
            case 1 -> "Reconnect " + concept + " to the main clue or rule it follows in " + topic + ".";
            case 2 -> "Practice how " + concept + " works in a simple example before adding complexity.";
            default -> "Focus on why " + concept + " is correct instead of memorizing a letter choice.";
        };
        List<String> distractors = List.of(
                "Skip the basics and jump straight to rare exceptions about " + concept + ".",
                "Replace " + concept + " with a similar-sounding topic even if the note context changes.",
                "Memorize one question pattern without reviewing the explanation behind " + concept + "."
        );
        String explanation = "Adaptive Practice is meant to rebuild weak concepts, so the best answer reinforces the core idea of "
                + concept + " in " + topic + " before moving into harder variations.";
        return new QuizItem(question, placeChoices(correctChoice, distractors, correctIndex), correctIndex, concept, explanation);
    }

    private List<String> placeChoices(String correctChoice, List<String> distractors, int correctIndex) {
        List<String> choices = new ArrayList<>(List.of("", "", "", ""));
        choices.set(correctIndex, correctChoice);
        int distractorIndex = 0;
        for (int index = 0; index < choices.size(); index++) {
            if (index == correctIndex) {
                continue;
            }
            choices.set(index, distractors.get(distractorIndex));
            distractorIndex++;
        }
        return choices;
    }

    private List<String> resolveConcepts(List<String> rawConcepts) {
        List<String> concepts = new ArrayList<>();
        if (rawConcepts != null) {
            for (String rawConcept : rawConcepts) {
                String normalized = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(rawConcept);
                if (normalized != null && !concepts.contains(normalized)) {
                    concepts.add(normalized);
                }
            }
        }
        return concepts.isEmpty() ? FALLBACK_CONCEPTS : concepts;
    }

    private String resolveTopicLabel(String studyPackTitle, StudyPackGenerationContext context) {
        String preferred = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(studyPackTitle);
        if (preferred == null && context != null) {
            preferred = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(context.subject());
        }
        if (preferred == null && context != null) {
            preferred = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(context.courseProgram());
        }
        if (preferred == null) {
            preferred = DEFAULT_TOPIC_LABEL;
        }
        return preferred.length() > MAX_TOPIC_LABEL_LENGTH
                ? preferred.substring(0, MAX_TOPIC_LABEL_LENGTH - 1).trim() + "..."
                : preferred;
    }

    private String resolveDifficultyFocus(String difficulty) {
        String normalizedDifficulty = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(difficulty);
        if (normalizedDifficulty == null) {
            return "core meaning";
        }
        return switch (normalizedDifficulty.toLowerCase()) {
            case "easy" -> "basic meaning";
            case "hard" -> "more advanced application";
            case "mixed" -> "exam-style application";
            default -> "core meaning";
        };
    }

    @FunctionalInterface
    private interface IndexedQuizItemBuilder {
        QuizItem build(int index);
    }
}
