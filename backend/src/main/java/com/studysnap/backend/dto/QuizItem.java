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
    private static final String MULTI_SELECT_FORMAT = "MULTI_SELECT";

    private final String question;
    private final List<String> choices;
    private final Integer correctIndex;
    private final List<Integer> correctIndices;
    private final String concept;
    private final String explanation;
    private final String questionFormat;
    private final String questionType;
    private final String workingSolution;
    private final String questionGroup;
    private final String keyConcept;
    private final List<String> acceptableAnswers;
    private final List<List<String>> acceptableAnswerGroups;
    private final String sourceStudyPackId;

    public QuizItem(
            String question,
            List<String> choices,
            int correctIndex,
            String concept,
            String explanation
    ) {
        this(question, choices, correctIndex, concept, explanation, null, null, null, null, null);
    }

    public QuizItem(
            String question,
            List<String> choices,
            String answer,
            String concept,
            String explanation
    ) {
        this(question, choices, null, concept, explanation, answer, null, null, null, null);
    }

    public QuizItem(
            String question,
            List<String> choices,
            Integer correctIndex,
            String concept,
            String explanation,
            String legacyAnswer
    ) {
        this(question, choices, correctIndex, concept, explanation, legacyAnswer, null, null, null, null);
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
        this(question, choices, correctIndex, concept, explanation, legacyAnswer, null, questionType, workingSolution, null);
    }

    public QuizItem(
            String question,
            List<String> choices,
            Integer correctIndex,
            String concept,
            String explanation,
            String legacyAnswer,
            String questionFormat,
            String questionType,
            String workingSolution
    ) {
        this(question, choices, correctIndex, concept, explanation, legacyAnswer, questionFormat, questionType, workingSolution, null);
    }

    public QuizItem(
            String question,
            List<String> choices,
            Integer correctIndex,
            String concept,
            String explanation,
            String legacyAnswer,
            String questionFormat,
            String questionType,
            String workingSolution,
            List<Integer> correctIndices
    ) {
        this(question, choices, correctIndex, concept, explanation, legacyAnswer, questionFormat, questionType, workingSolution, correctIndices, null);
    }

    public QuizItem(
            String question,
            List<String> choices,
            Integer correctIndex,
            String concept,
            String explanation,
            String legacyAnswer,
            String questionFormat,
            String questionType,
            String workingSolution,
            List<Integer> correctIndices,
            String questionGroup
    ) {
        this(
                question,
                choices,
                correctIndex,
                concept,
                explanation,
                legacyAnswer,
                questionFormat,
                questionType,
                workingSolution,
                correctIndices,
                questionGroup,
                null,
                null,
                null,
                null
        );
    }

    public QuizItem(
            String question,
            List<String> choices,
            Integer correctIndex,
            String concept,
            String explanation,
            String legacyAnswer,
            String questionFormat,
            String questionType,
            String workingSolution,
            List<Integer> correctIndices,
            String questionGroup,
            String keyConcept,
            List<String> acceptableAnswers,
            List<List<String>> acceptableAnswerGroups
    ) {
        this(
                question,
                choices,
                correctIndex,
                concept,
                explanation,
                legacyAnswer,
                questionFormat,
                questionType,
                workingSolution,
                correctIndices,
                questionGroup,
                keyConcept,
                acceptableAnswers,
                acceptableAnswerGroups,
                null
        );
    }

    private QuizItem(
            String question,
            List<String> choices,
            Integer correctIndex,
            String concept,
            String explanation,
            String legacyAnswer,
            String questionFormat,
            String questionType,
            String workingSolution,
            List<Integer> correctIndices,
            String questionGroup,
            String keyConcept,
            List<String> acceptableAnswers,
            List<List<String>> acceptableAnswerGroups,
            String sourceStudyPackId
    ) {
        this.question = question;
        this.choices = choices == null ? List.of() : List.copyOf(QuizValidationUtils.sanitizeChoiceTexts(choices));
        this.correctIndices = sanitizeCorrectIndices(correctIndices, this.choices.size());
        this.correctIndex = resolveCorrectIndex(this.choices, correctIndex, null, null, legacyAnswer, questionFormat, this.correctIndices);
        this.concept = concept;
        this.explanation = explanation;
        this.questionFormat = questionFormat;
        this.questionType = questionType;
        this.workingSolution = workingSolution;
        this.questionGroup = normalizeQuestionGroup(questionGroup);
        this.keyConcept = keyConcept;
        this.acceptableAnswers = sanitizeAcceptableAnswers(acceptableAnswers);
        this.acceptableAnswerGroups = sanitizeAcceptableAnswerGroups(acceptableAnswerGroups);
        this.sourceStudyPackId = sourceStudyPackId;
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
            @JsonProperty("questionFormat") String questionFormat,
            @JsonProperty("questionType") String questionType,
            @JsonProperty("workingSolution") String workingSolution,
            @JsonProperty("correctIndices") List<Integer> correctIndices,
            @JsonProperty("questionGroup") String questionGroup,
            @JsonProperty("keyConcept") String keyConcept,
            @JsonProperty("acceptableAnswers") List<String> acceptableAnswers,
            @JsonProperty("acceptableAnswerGroups") List<List<String>> acceptableAnswerGroups,
            @JsonProperty("sourceStudyPackId") String sourceStudyPackId
    ) {
        this(
                question,
                choices,
                resolveCorrectIndex(choices == null ? List.of() : List.copyOf(choices), correctIndex, legacyAnswerIndex, legacyCorrectAnswerIndex, legacyAnswer, questionFormat, correctIndices),
                concept,
                explanation,
                legacyAnswer,
                questionFormat,
                questionType,
                workingSolution,
                correctIndices,
                questionGroup,
                keyConcept,
                acceptableAnswers,
                acceptableAnswerGroups,
                sourceStudyPackId
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

    public List<Integer> correctIndices() {
        return correctIndices;
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

    public String questionFormat() {
        return questionFormat;
    }

    /**
     * MULTI_SELECT is the one format whose answer is a SET rather than an index, so callers outside this
     * class must be able to ask without minting another {@code "MULTI_SELECT"} literal (AGENTS.md forbids
     * a repeated literal spreading across classes).
     */
    public boolean isMultiSelect() {
        return MULTI_SELECT_FORMAT.equals(questionFormat);
    }

    public String questionType() {
        return questionType;
    }

    public String workingSolution() {
        return workingSolution;
    }

    public String questionGroup() {
        return questionGroup;
    }

    public String keyConcept() {
        return keyConcept;
    }

    public List<String> acceptableAnswers() {
        return acceptableAnswers;
    }

    public List<List<String>> acceptableAnswerGroups() {
        return acceptableAnswerGroups;
    }

    public String sourceStudyPackId() {
        return sourceStudyPackId;
    }

    /**
     * Returns a copy carrying {@code sourceStudyPackId}, reusing the already-sanitized field values
     * verbatim.
     *
     * <p>⚠️ This MUST NOT route through the sanitizing constructor.
     * {@code QuizValidationUtils.sanitizeChoiceText} strips a leading choice label
     * ({@code ^\s*[A-Da-d]\s*[.)]\s*}) with {@code replaceFirst} and is NOT idempotent, so a second
     * pass over already-sanitized choices strips a second token: {@code "A. B. Smith"} sanitizes to
     * {@code "B. Smith"} and then to {@code "Smith"}. Because {@code QuizSessionStateUtils.extractQuiz}
     * calls this on every deserialized item, a sanitizing copy would corrupt author initials and similar
     * text on every session load, in every quiz mode.
     */
    public QuizItem withSourceStudyPackId(String sourceStudyPackId) {
        return new QuizItem(this, sourceStudyPackId);
    }

    /**
     * Returns a copy with a different question group and format, reusing already-sanitized values.
     *
     * <p>⚠️ Same hazard as {@link #withSourceStudyPackId(String)}, and it bit a SECOND copy helper:
     * {@code sanitizeChoiceText} strips a leading choice label with {@code replaceFirst} and is NOT
     * idempotent, so rebuilding an already-sanitized item through the public constructor eats another
     * token — {@code "A. B. Smith"} becomes {@code "B. Smith"} and then {@code "Smith"}. MATCHING-group
     * demotion runs this on every item of an invalid group, in Challenge Quiz, Board Exam and Long Exam.
     *
     * <p>It also preserves {@code sourceStudyPackId}, which the previous rebuild dropped to null — latent
     * today because demotion runs before the service stamps provenance, but it would silently undo that
     * attribution the moment the order changed.
     */
    public QuizItem withQuestionGroupAndFormat(String questionGroup, String questionFormat) {
        QuizItem copy = new QuizItem(this, sourceStudyPackId);
        return new QuizItem(copy, questionGroup, questionFormat, sourceStudyPackId);
    }

    /** Trusted copy constructor for the group/format rewrite; no sanitizer is re-applied. */
    private QuizItem(QuizItem source, String questionGroup, String questionFormat, String sourceStudyPackId) {
        this.question = source.question;
        this.choices = source.choices;
        this.correctIndex = source.correctIndex;
        this.correctIndices = source.correctIndices;
        this.concept = source.concept;
        this.explanation = source.explanation;
        this.questionFormat = questionFormat;
        this.questionType = source.questionType;
        this.workingSolution = source.workingSolution;
        this.questionGroup = normalizeQuestionGroup(questionGroup);
        this.keyConcept = source.keyConcept;
        this.acceptableAnswers = source.acceptableAnswers;
        this.acceptableAnswerGroups = source.acceptableAnswerGroups;
        this.sourceStudyPackId = sourceStudyPackId;
    }

    /** Trusted copy constructor: every field is already sanitized, so no sanitizer is re-applied. */
    private QuizItem(QuizItem source, String sourceStudyPackId) {
        this.question = source.question;
        this.choices = source.choices;
        this.correctIndex = source.correctIndex;
        this.correctIndices = source.correctIndices;
        this.concept = source.concept;
        this.explanation = source.explanation;
        this.questionFormat = source.questionFormat;
        this.questionType = source.questionType;
        this.workingSolution = source.workingSolution;
        this.questionGroup = source.questionGroup;
        this.keyConcept = source.keyConcept;
        this.acceptableAnswers = source.acceptableAnswers;
        this.acceptableAnswerGroups = source.acceptableAnswerGroups;
        this.sourceStudyPackId = sourceStudyPackId;
    }

    private static Integer resolveCorrectIndex(
            List<String> choices,
            Integer correctIndex,
            Integer legacyAnswerIndex,
            Integer legacyCorrectAnswerIndex,
            String legacyAnswer,
            String questionFormat,
            List<Integer> correctIndices
    ) {
        Integer indexedAnswer = firstValidIndex(choices, correctIndex, legacyAnswerIndex, legacyCorrectAnswerIndex);
        if (indexedAnswer != null) {
            return indexedAnswer;
        }
        Integer multiSelectFallback = firstValidCorrectIndex(choices, questionFormat, correctIndices);
        if (multiSelectFallback != null) {
            return multiSelectFallback;
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

    private static List<Integer> sanitizeCorrectIndices(List<Integer> correctIndices, int choiceCount) {
        if (correctIndices == null || correctIndices.isEmpty() || choiceCount <= 0) {
            return List.of();
        }
        return correctIndices.stream()
                .filter(Objects::nonNull)
                .filter(index -> index >= 0 && index < choiceCount)
                .distinct()
                .toList();
    }

    private static Integer firstValidCorrectIndex(List<String> choices, String questionFormat, List<Integer> correctIndices) {
        if (!MULTI_SELECT_FORMAT.equals(questionFormat) || correctIndices == null || correctIndices.isEmpty()) {
            return null;
        }
        int choiceCount = choices == null ? 0 : choices.size();
        Integer first = correctIndices.getFirst();
        return first >= 0 && first < choiceCount ? first : null;
    }

    private static String normalizeQuestionGroup(String questionGroup) {
        if (questionGroup == null || questionGroup.isBlank()) {
            return null;
        }
        return questionGroup.trim();
    }

    private static List<String> sanitizeAcceptableAnswers(List<String> acceptableAnswers) {
        if (acceptableAnswers == null || acceptableAnswers.isEmpty()) {
            return List.of();
        }
        return acceptableAnswers.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static List<List<String>> sanitizeAcceptableAnswerGroups(List<List<String>> acceptableAnswerGroups) {
        if (acceptableAnswerGroups == null || acceptableAnswerGroups.isEmpty()) {
            return List.of();
        }
        return acceptableAnswerGroups.stream()
                .filter(Objects::nonNull)
                .map(QuizItem::sanitizeAcceptableAnswers)
                .toList();
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
                && Objects.equals(correctIndices, quizItem.correctIndices)
                && Objects.equals(concept, quizItem.concept)
                && Objects.equals(explanation, quizItem.explanation)
                && Objects.equals(questionFormat, quizItem.questionFormat)
                && Objects.equals(questionType, quizItem.questionType)
                && Objects.equals(workingSolution, quizItem.workingSolution)
                && Objects.equals(questionGroup, quizItem.questionGroup)
                && Objects.equals(keyConcept, quizItem.keyConcept)
                && Objects.equals(acceptableAnswers, quizItem.acceptableAnswers)
                && Objects.equals(acceptableAnswerGroups, quizItem.acceptableAnswerGroups)
                && Objects.equals(sourceStudyPackId, quizItem.sourceStudyPackId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(question, choices, correctIndex, correctIndices, concept, explanation, questionFormat, questionType, workingSolution, questionGroup, keyConcept, acceptableAnswers, acceptableAnswerGroups, sourceStudyPackId);
    }

    @Override
    public String toString() {
        return "QuizItem{"
                + "question='" + question + '\''
                + ", choices=" + choices
                + ", correctIndex=" + correctIndex
                + ", correctIndices=" + correctIndices
                + ", concept='" + concept + '\''
                + ", explanation='" + explanation + '\''
                + ", questionFormat='" + questionFormat + '\''
                + ", questionType='" + questionType + '\''
                + ", workingSolution='" + workingSolution + '\''
                + ", questionGroup='" + questionGroup + '\''
                + ", keyConcept='" + keyConcept + '\''
                + ", acceptableAnswers=" + acceptableAnswers
                + ", acceptableAnswerGroups=" + acceptableAnswerGroups
                + ", sourceStudyPackId='" + sourceStudyPackId + '\''
                + '}';
    }
}
