package com.studysnap.backend.util;

import com.studysnap.backend.dto.QuizItem;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@UtilityClass
public class QuizSessionStateUtils {
    private static final String QUIZ_KEY = "quiz";
    private static final String QUESTION_KEY = "question";
    private static final String CHOICES_KEY = "choices";
    private static final String CORRECT_INDEX_KEY = "correctIndex";
    private static final String ANSWER_INDEX_KEY = "answerIndex";
    private static final String CORRECT_ANSWER_INDEX_KEY = "correctAnswerIndex";
    private static final String ANSWER_KEY = "answer";
    private static final String CONCEPT_KEY = "concept";
    private static final String EXPLANATION_KEY = "explanation";
    private static final String SELECTED_CHOICES_KEY = "selectedChoices";

    public Map<String, Object> appendQuizItems(Map<String, Object> sessionState, List<QuizItem> newItems) {
        List<QuizItem> existing = extractQuiz(sessionState);
        List<QuizItem> combined = new ArrayList<>(existing);
        combined.addAll(newItems);
        return withQuiz(combined, sessionState);
    }

    public Map<String, Object> withQuiz(List<QuizItem> quiz, Map<String, Object> baseState) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (baseState != null && !baseState.isEmpty()) {
            state.putAll(baseState);
        }
        state.put(QUIZ_KEY, serializeQuiz(quiz));
        return state;
    }

    public List<QuizItem> extractQuiz(Map<String, Object> sessionState) {
        if (sessionState == null) {
            return List.of();
        }
        Object rawQuiz = sessionState.get(QUIZ_KEY);
        if (!(rawQuiz instanceof List<?> rawList)) {
            return List.of();
        }

        List<QuizItem> quiz = new ArrayList<>();
        for (Object rawItem : rawList) {
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Object questionRaw = rawMap.get(QUESTION_KEY);
            Object correctIndexRaw = rawMap.get(CORRECT_INDEX_KEY);
            Object answerIndexRaw = rawMap.get(ANSWER_INDEX_KEY);
            Object correctAnswerIndexRaw = rawMap.get(CORRECT_ANSWER_INDEX_KEY);
            Object answerRaw = rawMap.get(ANSWER_KEY);
            Object conceptRaw = rawMap.get(CONCEPT_KEY);
            Object explanationRaw = rawMap.get(EXPLANATION_KEY);
            Object choicesRaw = rawMap.get(CHOICES_KEY);
            if (!(questionRaw instanceof String question)) {
                continue;
            }

            List<String> choices = new ArrayList<>();
            if (choicesRaw instanceof List<?> rawChoices) {
                for (Object choice : rawChoices) {
                    if (choice instanceof String value && !value.isBlank()) {
                        choices.add(value);
                    }
                }
            }
            if (choices.isEmpty()) {
                continue;
            }

            String concept = conceptRaw instanceof String value ? value : null;
            String explanation = explanationRaw instanceof String value ? value : null;
            Integer correctIndex = parseCorrectIndex(choices.size(), correctIndexRaw, answerIndexRaw, correctAnswerIndexRaw);
            String answer = answerRaw instanceof String value ? value : null;
            quiz.add(new QuizItem(question, choices, correctIndex, concept, explanation, answer));
        }

        return quiz;
    }

    public Map<Integer, Integer> extractSelectedChoiceIndexes(Map<String, Object> sessionState, List<QuizItem> quiz) {
        if (sessionState == null || sessionState.isEmpty()) {
            return Map.of();
        }
        Object raw = sessionState.get(SELECTED_CHOICES_KEY);
        if (!(raw instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return Map.of();
        }

        Map<Integer, Integer> selectedChoices = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            Object key = entry.getKey();
            if (!(key instanceof String keyString)) {
                continue;
            }
            try {
                int questionIndex = Integer.parseInt(keyString);
                if (questionIndex < 0 || questionIndex >= quiz.size()) {
                    continue;
                }
                Integer selectedChoiceIndex = resolveSelectedChoiceIndex(entry.getValue(), quiz.get(questionIndex));
                if (selectedChoiceIndex != null) {
                    selectedChoices.put(questionIndex, selectedChoiceIndex);
                }
            } catch (NumberFormatException ignored) {
                // Ignore invalid question index keys.
            }
        }
        return selectedChoices.isEmpty() ? Map.of() : Map.copyOf(selectedChoices);
    }

    private List<Map<String, Object>> serializeQuiz(List<QuizItem> quiz) {
        if (quiz == null || quiz.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> serialized = new ArrayList<>(quiz.size());
        for (QuizItem item : quiz) {
            if (item == null) {
                continue;
            }
            Map<String, Object> quizItem = new LinkedHashMap<>();
            quizItem.put(QUESTION_KEY, item.question());
            quizItem.put(CHOICES_KEY, item.choices() == null ? List.of() : new ArrayList<>(item.choices()));
            quizItem.put(CORRECT_INDEX_KEY, item.correctIndex());
            quizItem.put(CONCEPT_KEY, item.concept());
            quizItem.put(EXPLANATION_KEY, item.explanation());
            serialized.add(quizItem);
        }
        return serialized;
    }

    private Integer parseCorrectIndex(int choiceCount, Object... candidates) {
        for (Object value : candidates) {
            if (value instanceof Number number) {
                int parsed = number.intValue();
                if (parsed >= 0 && parsed < choiceCount) {
                    return parsed;
                }
            }
            if (value instanceof String raw) {
                try {
                    int parsed = Integer.parseInt(raw.trim());
                    if (parsed >= 0 && parsed < choiceCount) {
                        return parsed;
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore invalid numeric payloads and keep checking fallbacks.
                }
            }
        }
        return null;
    }

    private Integer resolveSelectedChoiceIndex(Object rawValue, QuizItem item) {
        if (item == null || item.choices() == null || item.choices().isEmpty()) {
            return null;
        }
        if (rawValue instanceof Number number) {
            int selectedChoiceIndex = number.intValue();
            return selectedChoiceIndex >= 0 && selectedChoiceIndex < item.choices().size() ? selectedChoiceIndex : null;
        }
        if (rawValue instanceof String selectedChoice) {
            for (int index = 0; index < item.choices().size(); index++) {
                if (Objects.equals(item.choices().get(index), selectedChoice)) {
                    return index;
                }
            }
        }
        return null;
    }
}
