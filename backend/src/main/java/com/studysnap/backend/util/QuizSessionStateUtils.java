package com.studysnap.backend.util;

import com.studysnap.backend.dto.QuizItem;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@UtilityClass
public class QuizSessionStateUtils {
    private static final String QUIZ_KEY = "quiz";
    private static final String QUESTION_KEY = "question";
    private static final String CHOICES_KEY = "choices";
    private static final String ANSWER_KEY = "answer";
    private static final String CONCEPT_KEY = "concept";
    private static final String EXPLANATION_KEY = "explanation";

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
            Object answerRaw = rawMap.get(ANSWER_KEY);
            Object conceptRaw = rawMap.get(CONCEPT_KEY);
            Object explanationRaw = rawMap.get(EXPLANATION_KEY);
            Object choicesRaw = rawMap.get(CHOICES_KEY);
            if (!(questionRaw instanceof String question) || !(answerRaw instanceof String answer)) {
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
            quiz.add(new QuizItem(question, choices, answer, concept, explanation));
        }

        return quiz;
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
            quizItem.put(ANSWER_KEY, item.answer());
            quizItem.put(CONCEPT_KEY, item.concept());
            quizItem.put(EXPLANATION_KEY, item.explanation());
            serialized.add(quizItem);
        }
        return serialized;
    }
}
