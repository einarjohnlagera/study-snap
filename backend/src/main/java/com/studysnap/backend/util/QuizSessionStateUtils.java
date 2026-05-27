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
    private static final String CORRECT_INDICES_KEY = "correctIndices";
    private static final String ANSWER_INDEX_KEY = "answerIndex";
    private static final String CORRECT_ANSWER_INDEX_KEY = "correctAnswerIndex";
    private static final String ANSWER_KEY = "answer";
    private static final String CONCEPT_KEY = "concept";
    private static final String EXPLANATION_KEY = "explanation";
    private static final String QUESTION_FORMAT_KEY = "questionFormat";
    private static final String QUESTION_TYPE_KEY = "questionType";
    private static final String WORKING_SOLUTION_KEY = "workingSolution";
    private static final String SELECTED_CHOICES_KEY = "selectedChoices";
    private static final String SELECTED_MULTI_CHOICES_KEY = "selectedMultiChoices";
    private static final String SUB_MODE_KEY = "subMode";
    private static final String AI_FEEDBACK_KEY = "aiFeedback";
    private static final String SOFT_TIMER_SECONDS_KEY = "softTimerSeconds";
    private static final String TIME_SPENT_SECONDS_KEY = "timeSpentSeconds";
    public static final String SESSION_STATE_POOL_SOURCED = "poolSourced";

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

    public Map<String, Object> withSelectedChoice(
            Map<String, Object> sessionState,
            int questionIndex,
            int choiceIndex
    ) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (sessionState != null && !sessionState.isEmpty()) {
            state.putAll(sessionState);
        }

        Map<String, Object> selectedChoices = new LinkedHashMap<>();
        Object existingSelectedChoices = state.get(SELECTED_CHOICES_KEY);
        if (existingSelectedChoices instanceof Map<?, ?> existingMap) {
            for (Map.Entry<?, ?> entry : existingMap.entrySet()) {
                if (entry.getKey() != null) {
                    selectedChoices.put(entry.getKey().toString(), entry.getValue());
                }
            }
        }
        selectedChoices.put(String.valueOf(questionIndex), choiceIndex);
        state.put(SELECTED_CHOICES_KEY, selectedChoices);
        return state;
    }

    public Map<String, Object> writeSelectedMultiChoice(
            Map<String, Object> sessionState,
            int questionIndex,
            List<Integer> choiceIndices
    ) {
        return withSelectedMultiChoice(sessionState, questionIndex, choiceIndices);
    }

    public Map<String, Object> withSelectedMultiChoice(
            Map<String, Object> sessionState,
            int questionIndex,
            List<Integer> choiceIndices
    ) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (sessionState != null && !sessionState.isEmpty()) {
            state.putAll(sessionState);
        }

        Map<String, Object> selectedChoices = copyStringKeyMap(state.get(SELECTED_MULTI_CHOICES_KEY));
        selectedChoices.put(String.valueOf(questionIndex), normalizeIntegerList(choiceIndices));
        state.put(SELECTED_MULTI_CHOICES_KEY, selectedChoices);
        return state;
    }

    public Map<String, Object> clearSelectedMultiChoices(Map<String, Object> sessionState) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (sessionState != null && !sessionState.isEmpty()) {
            state.putAll(sessionState);
        }
        state.put(SELECTED_MULTI_CHOICES_KEY, Map.of());
        return state;
    }

    public Map<String, Object> withInterviewPracticeState(
            List<QuizItem> quiz,
            String subMode,
            int softTimerSeconds
    ) {
        Map<String, Object> state = withQuiz(quiz, null);
        state.put(SUB_MODE_KEY, subMode);
        state.put(AI_FEEDBACK_KEY, List.of());
        state.put(SOFT_TIMER_SECONDS_KEY, softTimerSeconds);
        state.put(SELECTED_CHOICES_KEY, Map.of());
        state.put(TIME_SPENT_SECONDS_KEY, Map.of());
        return state;
    }

    public Map<String, Object> withPoolSourced(Map<String, Object> sessionState, boolean poolSourced) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (sessionState != null && !sessionState.isEmpty()) {
            state.putAll(sessionState);
        }
        state.put(SESSION_STATE_POOL_SOURCED, poolSourced);
        return state;
    }

    public boolean extractPoolSourced(Map<String, Object> sessionState) {
        if (sessionState == null || sessionState.isEmpty()) {
            return false;
        }
        Object raw = sessionState.get(SESSION_STATE_POOL_SOURCED);
        if (raw instanceof Boolean value) {
            return value;
        }
        return raw instanceof String value && Boolean.parseBoolean(value);
    }

    public Map<String, Object> withInterviewAnswer(
            Map<String, Object> sessionState,
            int questionIndex,
            int choiceIndex,
            int timeSpentSeconds
    ) {
        Map<String, Object> state = withSelectedChoice(sessionState, questionIndex, choiceIndex);
        Map<String, Object> timeSpentByQuestion = copyStringKeyMap(state.get(TIME_SPENT_SECONDS_KEY));
        timeSpentByQuestion.put(String.valueOf(questionIndex), timeSpentSeconds);
        state.put(TIME_SPENT_SECONDS_KEY, timeSpentByQuestion);
        return state;
    }

    public Map<String, Object> withInterviewFeedback(
            Map<String, Object> sessionState,
            int questionIndex,
            Map<String, Object> feedback
    ) {
        Map<String, Object> state = new LinkedHashMap<>();
        if (sessionState != null && !sessionState.isEmpty()) {
            state.putAll(sessionState);
        }
        List<Object> feedbackItems = new ArrayList<>();
        Object raw = state.get(AI_FEEDBACK_KEY);
        if (raw instanceof List<?> rawList) {
            feedbackItems.addAll(rawList);
        }
        while (feedbackItems.size() <= questionIndex) {
            feedbackItems.add(null);
        }
        feedbackItems.set(questionIndex, feedback == null ? Map.of() : new LinkedHashMap<>(feedback));
        state.put(AI_FEEDBACK_KEY, feedbackItems);
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
            Object correctIndicesRaw = rawMap.get(CORRECT_INDICES_KEY);
            Object conceptRaw = rawMap.get(CONCEPT_KEY);
            Object explanationRaw = rawMap.get(EXPLANATION_KEY);
            Object questionFormatRaw = rawMap.get(QUESTION_FORMAT_KEY);
            Object questionTypeRaw = rawMap.get(QUESTION_TYPE_KEY);
            Object workingSolutionRaw = rawMap.get(WORKING_SOLUTION_KEY);
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
            List<Integer> correctIndices = parseIndexList(choices.size(), correctIndicesRaw);
            String answer = answerRaw instanceof String value ? value : null;
            String questionFormat = questionFormatRaw instanceof String value ? value : null;
            String questionType = questionTypeRaw instanceof String value ? value : null;
            String workingSolution = workingSolutionRaw instanceof String value ? value : null;
            quiz.add(new QuizItem(question, choices, correctIndex, concept, explanation, answer, questionFormat, questionType, workingSolution, correctIndices));
        }

        return quiz;
    }

    public String extractSubMode(Map<String, Object> sessionState) {
        if (sessionState == null) {
            return null;
        }
        Object raw = sessionState.get(SUB_MODE_KEY);
        return raw instanceof String value && !value.isBlank() ? value : null;
    }

    public Map<Integer, Integer> extractInterviewTimeSpentSeconds(Map<String, Object> sessionState, List<QuizItem> quiz) {
        if (sessionState == null || sessionState.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> rawMap = copyStringKeyMap(sessionState.get(TIME_SPENT_SECONDS_KEY));
        if (rawMap.isEmpty()) {
            return Map.of();
        }

        Map<Integer, Integer> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
            try {
                int questionIndex = Integer.parseInt(entry.getKey());
                if (questionIndex < 0 || questionIndex >= quiz.size()) {
                    continue;
                }
                if (entry.getValue() instanceof Number number) {
                    parsed.put(questionIndex, number.intValue());
                }
            } catch (NumberFormatException ignored) {
                // Ignore invalid question index keys.
            }
        }
        return parsed.isEmpty() ? Map.of() : Map.copyOf(parsed);
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

    public Map<Integer, List<Integer>> readSelectedMultiChoices(Map<String, Object> sessionState, List<QuizItem> quiz) {
        return extractSelectedMultiChoiceIndexes(sessionState, quiz);
    }

    public Map<Integer, List<Integer>> readSelectedMultiChoices(Map<String, Object> sessionState) {
        if (sessionState == null || sessionState.isEmpty()) {
            return Map.of();
        }
        Object raw = sessionState.get(SELECTED_MULTI_CHOICES_KEY);
        if (!(raw instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<Integer>> selectedChoices = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String keyString)) {
                continue;
            }
            try {
                int questionIndex = Integer.parseInt(keyString);
                List<Integer> choiceIndexes = normalizeUnknownChoiceIndexList(entry.getValue());
                if (questionIndex >= 0 && !choiceIndexes.isEmpty()) {
                    selectedChoices.put(questionIndex, choiceIndexes);
                }
            } catch (NumberFormatException ignored) {
                // Ignore invalid question index keys.
            }
        }
        return selectedChoices.isEmpty() ? Map.of() : Map.copyOf(selectedChoices);
    }

    public Map<Integer, List<Integer>> extractSelectedMultiChoiceIndexes(Map<String, Object> sessionState, List<QuizItem> quiz) {
        if (sessionState == null || sessionState.isEmpty()) {
            return Map.of();
        }
        Object raw = sessionState.get(SELECTED_MULTI_CHOICES_KEY);
        if (!(raw instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return Map.of();
        }

        Map<Integer, List<Integer>> selectedChoices = new LinkedHashMap<>();
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
                List<Integer> selectedChoiceIndexes = resolveSelectedChoiceIndexes(entry.getValue(), quiz.get(questionIndex));
                if (!selectedChoiceIndexes.isEmpty()) {
                    selectedChoices.put(questionIndex, selectedChoiceIndexes);
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
            quizItem.put(CORRECT_INDICES_KEY, item.correctIndices() == null ? List.of() : new ArrayList<>(item.correctIndices()));
            quizItem.put(CONCEPT_KEY, item.concept());
            quizItem.put(EXPLANATION_KEY, item.explanation());
            quizItem.put(QUESTION_FORMAT_KEY, item.questionFormat());
            quizItem.put(QUESTION_TYPE_KEY, item.questionType());
            quizItem.put(WORKING_SOLUTION_KEY, item.workingSolution());
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

    private List<Integer> resolveSelectedChoiceIndexes(Object rawValue, QuizItem item) {
        if (item == null || item.choices() == null || item.choices().isEmpty() || !(rawValue instanceof List<?> rawList)) {
            return List.of();
        }
        List<Integer> selectedChoiceIndexes = new ArrayList<>();
        for (Object rawItem : rawList) {
            Integer selectedChoiceIndex = resolveSelectedChoiceIndex(rawItem, item);
            if (selectedChoiceIndex != null && !selectedChoiceIndexes.contains(selectedChoiceIndex)) {
                selectedChoiceIndexes.add(selectedChoiceIndex);
            }
        }
        return selectedChoiceIndexes;
    }

    private List<Integer> parseIndexList(int choiceCount, Object rawValue) {
        if (!(rawValue instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }
        List<Integer> indexes = new ArrayList<>();
        for (Object rawItem : rawList) {
            Integer parsed = parseCorrectIndex(choiceCount, rawItem);
            if (parsed != null && !indexes.contains(parsed)) {
                indexes.add(parsed);
            }
        }
        return indexes;
    }

    private List<Integer> normalizeIntegerList(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<Integer> normalizeUnknownChoiceIndexList(Object rawValue) {
        if (!(rawValue instanceof List<?> rawList)) {
            return List.of();
        }
        List<Integer> indexes = new ArrayList<>();
        for (Object rawItem : rawList) {
            Integer parsed = null;
            if (rawItem instanceof Number number) {
                parsed = number.intValue();
            } else if (rawItem instanceof String value) {
                try {
                    parsed = Integer.parseInt(value.trim());
                } catch (NumberFormatException ignored) {
                    // Ignore invalid numeric payloads.
                }
            }
            if (parsed != null && parsed >= 0 && !indexes.contains(parsed)) {
                indexes.add(parsed);
            }
        }
        return indexes;
    }

    private Map<String, Object> copyStringKeyMap(Object raw) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    copy.put(entry.getKey().toString(), entry.getValue());
                }
            }
        }
        return copy;
    }
}
