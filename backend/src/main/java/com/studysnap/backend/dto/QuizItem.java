package com.studysnap.backend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.studysnap.backend.util.QuizValidationUtils;

import java.util.List;
import java.util.Objects;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public final class QuizItem {
    private final String question;
    private final List<String> choices;
    private final Integer correctIndex;
    private final String concept;
    private final String explanation;
    private final String questionType;
    private final String workingSolution;

    public QuizItem(
            String question,
            List<String> choices,
            int correctIndex,
            String concept,
            String explanation
    ) {
        this(question, choices, correctIndex, concept, explanation, null, null, null);
    }

    public QuizItem(
            String question,
            List<String> choices,
            String answer,
            String concept,
            String explanation
    ) {
        this(question, choices, null, concept, explanation, answer, null, null);
    }

    public QuizItem(
            String question,
            List<String> choices,
            Integer correctIndex,
            String concept,
            String explanation,
            String legacyAnswer
    ) {
        this(question, choices, correctIndex, concept, explanation, legacyAnswer, null, null);
    }

    public QuizItem(
            String question,
            List<String> choices,
            Integer correctIndex,
            String concept,
            String explanation,
            String legacyAnswer,
            String questionType,
            String workingSolution
    ) {
        this.question = question;
        this.choices = choices == null ? List.of() : List.copyOf(QuizValidationUtils.sanitizeChoiceTexts(choices));
        this.correctIndex = resolveCorrectIndex(this.choices, correctIndex, null, null, legacyAnswer);
        this.concept = concept;
        this.explanation = explanation;
        this.questionType = questionType;
        this.workingSolution = workingSolution;
    }

    @JsonCreator
    public QuizItem(
            @JsonProperty("question") String question,
            @JsonProperty("choices") List<String> choices,
            @JsonProperty("correctIndex") Integer correctIndex,
            @JsonProperty("concept") String concept,
            @JsonProperty("explanation") String explanation,
            @JsonProperty("answer") String legacyAnswer,
            @JsonProperty("answerIndex") Integer legacyAnswerIndex,
            @JsonProperty("correctAnswerIndex") Integer legacyCorrectAnswerIndex,
            @JsonProperty("questionType") String questionType,
            @JsonProperty("workingSolution") String workingSolution
    ) {
        this(
                question,
                choices,
                resolveCorrectIndex(choices == null ? List.of() : List.copyOf(choices), correctIndex, legacyAnswerIndex, legacyCorrectAnswerIndex, legacyAnswer),
                concept,
                explanation,
                legacyAnswer,
                questionType,
                workingSolution
        );
    }

    public String question() {
        return question;
    }

    public List<String> choices() {
        return choices;
    }

    public Integer correctIndex() {
        return correctIndex;
    }

    @JsonIgnore
    public String answer() {
        if (correctIndex == null || correctIndex < 0 || correctIndex >= choices.size()) {
            return null;
        }
        return choices.get(correctIndex);
    }

    public String concept() {
        return concept;
    }

    public String explanation() {
        return explanation;
    }

    public String questionType() {
        return questionType;
    }

    public String workingSolution() {
        return workingSolution;
    }

    private static Integer resolveCorrectIndex(
            List<String> choices,
            Integer correctIndex,
            Integer legacyAnswerIndex,
            Integer legacyCorrectAnswerIndex,
            String legacyAnswer
    ) {
        Integer indexedAnswer = firstValidIndex(choices, correctIndex, legacyAnswerIndex, legacyCorrectAnswerIndex);
        if (indexedAnswer != null) {
            return indexedAnswer;
        }
        if (legacyAnswer == null || choices == null || choices.isEmpty()) {
            return null;
        }
        String normalizedLegacyAnswer = QuizValidationUtils.sanitizeChoiceText(legacyAnswer);
        if (normalizedLegacyAnswer == null) {
            return answerLetterIndex(legacyAnswer, choices.size());
        }
        for (int index = 0; index < choices.size(); index++) {
            if (Objects.equals(choices.get(index), normalizedLegacyAnswer)) {
                return index;
            }
        }
        return answerLetterIndex(normalizedLegacyAnswer, choices.size());
    }

    private static Integer answerLetterIndex(String answer, int choiceCount) {
        if (answer == null || choiceCount <= 0) {
            return null;
        }
        String normalized = answer.trim();
        if (normalized.length() == 2 && (normalized.charAt(1) == '.' || normalized.charAt(1) == ')')) {
            normalized = normalized.substring(0, 1);
        }
        if (normalized.length() != 1) {
            return null;
        }
        int index = Character.toUpperCase(normalized.charAt(0)) - 'A';
        return index >= 0 && index < choiceCount ? index : null;
    }

    private static Integer firstValidIndex(List<String> choices, Integer... candidates) {
        int choiceCount = choices == null ? 0 : choices.size();
        for (Integer candidate : candidates) {
            if (candidate != null && candidate >= 0 && candidate < choiceCount) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuizItem quizItem)) {
            return false;
        }
        return Objects.equals(question, quizItem.question)
                && Objects.equals(choices, quizItem.choices)
                && Objects.equals(correctIndex, quizItem.correctIndex)
                && Objects.equals(concept, quizItem.concept)
                && Objects.equals(explanation, quizItem.explanation)
                && Objects.equals(questionType, quizItem.questionType)
                && Objects.equals(workingSolution, quizItem.workingSolution);
    }

    @Override
    public int hashCode() {
        return Objects.hash(question, choices, correctIndex, concept, explanation, questionType, workingSolution);
    }

    @Override
    public String toString() {
        return "QuizItem{"
                + "question='" + question + '\''
                + ", choices=" + choices
                + ", correctIndex=" + correctIndex
                + ", concept='" + concept + '\''
                + ", explanation='" + explanation + '\''
                + ", questionType='" + questionType + '\''
                + ", workingSolution='" + workingSolution + '\''
                + '}';
    }
}
