package com.studysnap.backend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    public QuizItem(
            String question,
            List<String> choices,
            int correctIndex,
            String concept,
            String explanation
    ) {
        this(question, choices, correctIndex, concept, explanation, null);
    }

    public QuizItem(
            String question,
            List<String> choices,
            String answer,
            String concept,
            String explanation
    ) {
        this(question, choices, null, concept, explanation, answer);
    }

    public QuizItem(
            String question,
            List<String> choices,
            Integer correctIndex,
            String concept,
            String explanation,
            String legacyAnswer
    ) {
        this.question = question;
        this.choices = choices == null ? List.of() : List.copyOf(choices);
        this.correctIndex = resolveCorrectIndex(this.choices, correctIndex, null, null, legacyAnswer);
        this.concept = concept;
        this.explanation = explanation;
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
            @JsonProperty("correctAnswerIndex") Integer legacyCorrectAnswerIndex
    ) {
        this(
                question,
                choices,
                resolveCorrectIndex(choices == null ? List.of() : List.copyOf(choices), correctIndex, legacyAnswerIndex, legacyCorrectAnswerIndex, legacyAnswer),
                concept,
                explanation,
                legacyAnswer
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
        for (int index = 0; index < choices.size(); index++) {
            if (Objects.equals(choices.get(index), legacyAnswer)) {
                return index;
            }
        }
        return null;
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
                && Objects.equals(explanation, quizItem.explanation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(question, choices, correctIndex, concept, explanation);
    }

    @Override
    public String toString() {
        return "QuizItem{"
                + "question='" + question + '\''
                + ", choices=" + choices
                + ", correctIndex=" + correctIndex
                + ", concept='" + concept + '\''
                + ", explanation='" + explanation + '\''
                + '}';
    }
}
