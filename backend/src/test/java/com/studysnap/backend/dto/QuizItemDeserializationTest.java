package com.studysnap.backend.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuizItemDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesComputationalFields() throws Exception {
        QuizItem item = objectMapper.readValue(
                """
                {
                  "question": "What is the power?",
                  "choices": ["5 W", "10 W", "20 W", "25 W"],
                  "correctIndex": 1,
                  "concept": "Electrical power",
                  "explanation": "Power is voltage multiplied by current.",
                  "questionType": "COMPUTATIONAL",
                  "workingSolution": "P = IV = 5 × 2 = 10 W"
                }
                """,
                QuizItem.class
        );

        assertThat(item.questionType()).isEqualTo("COMPUTATIONAL");
        assertThat(item.workingSolution()).isEqualTo("P = IV = 5 × 2 = 10 W");
        assertThat(item.answer()).isEqualTo("10 W");
    }

    @Test
    void deserializesMissingComputationalFieldsAsNull() throws Exception {
        QuizItem item = objectMapper.readValue(
                """
                {
                  "question": "What is voltage?",
                  "choices": ["Current", "Potential difference", "Resistance", "Power"],
                  "correctIndex": 1,
                  "concept": "Voltage",
                  "explanation": "Voltage is electric potential difference."
                }
                """,
                QuizItem.class
        );

        assertThat(item.questionType()).isNull();
        assertThat(item.workingSolution()).isNull();
        assertThat(item.questionFormat()).isNull();
    }

    @Test
    void deserializesAndSerializesKeyConcept() throws Exception {
        QuizItem item = objectMapper.readValue(
                """
                {
                  "question": "Which stage generates most ATP?",
                  "choices": ["Glycolysis", "Citric acid cycle", "Electron transport chain", "Fermentation"],
                  "correctIndex": 2,
                  "concept": "Energy systems",
                  "keyConcept": "Electron transport chain",
                  "explanation": "The electron transport chain produces most ATP."
                }
                """,
                QuizItem.class
        );

        String serialized = objectMapper.writeValueAsString(item);

        assertThat(item.concept()).isEqualTo("Energy systems");
        assertThat(item.keyConcept()).isEqualTo("Electron transport chain");
        assertThat(serialized).contains("\"keyConcept\":\"Electron transport chain\"");
    }

    @Test
    void deserializesAndSerializesAcceptableAnswers() throws Exception {
        QuizItem item = objectMapper.readValue(
                """
                {
                  "question": "Identify the law that relates voltage, current, and resistance.",
                  "choices": [],
                  "correctIndex": null,
                  "concept": "Ohm's Law",
                  "keyConcept": "Ohm's Law",
                  "explanation": "Ohm's Law relates voltage, current, and resistance.",
                  "questionFormat": "IDENTIFICATION",
                  "acceptableAnswers": ["Ohm's Law", "Ohms law"]
                }
                """,
                QuizItem.class
        );

        String serialized = objectMapper.writeValueAsString(item);

        assertThat(item.choices()).isEmpty();
        assertThat(item.correctIndex()).isNull();
        assertThat(item.acceptableAnswers()).containsExactly("Ohm's Law", "Ohms law");
        assertThat(serialized).contains("\"acceptableAnswers\":[\"Ohm's Law\",\"Ohms law\"]");
    }

    @Test
    void deserializesMissingKeyConceptAsNull() throws Exception {
        QuizItem item = objectMapper.readValue(
                """
                {
                  "question": "What is voltage?",
                  "choices": ["Current", "Potential difference", "Resistance", "Power"],
                  "correctIndex": 1,
                  "concept": "Voltage",
                  "explanation": "Voltage is electric potential difference."
                }
                """,
                QuizItem.class
        );

        assertThat(item.keyConcept()).isNull();
    }

    @Test
    void deserializesTrueFalseFormatAndLetterAnswer() throws Exception {
        QuizItem item = objectMapper.readValue(
                """
                {
                  "question": "Ohm's Law states that voltage is directly proportional to current.",
                  "choices": ["True", "False"],
                  "answer": "B",
                  "concept": "Ohm's Law",
                  "explanation": "Ohm's Law relates voltage, current, and resistance.",
                  "questionFormat": "TRUE_FALSE"
                }
                """,
                QuizItem.class
        );

        assertThat(item.questionFormat()).isEqualTo("TRUE_FALSE");
        assertThat(item.correctIndex()).isEqualTo(1);
        assertThat(item.answer()).isEqualTo("False");
    }

    @Test
    void deserializesMultiSelectWithCorrectIndexFallback() throws Exception {
        QuizItem item = objectMapper.readValue(
                """
                {
                  "question": "Which properties describe acids?",
                  "choices": ["Donate protons", "Taste bitter", "Turn blue litmus red", "Release hydroxide ions"],
                  "correctIndices": [0, 2],
                  "concept": "Acids",
                  "explanation": "Acids donate protons and turn blue litmus red.",
                  "questionFormat": "MULTI_SELECT"
                }
                """,
                QuizItem.class
        );

        assertThat(item.questionFormat()).isEqualTo("MULTI_SELECT");
        assertThat(item.correctIndices()).containsExactly(0, 2);
        assertThat(item.correctIndex()).isZero();
        assertThat(item.answer()).isEqualTo("Donate protons");
    }

    @Test
    void deserializesMatchingQuestionGroup() throws Exception {
        QuizItem item = objectMapper.readValue(
                """
                {
                  "question": "Pressure applied to a confined fluid is transmitted equally.",
                  "choices": ["Bernoulli's Principle", "Pascal's Law", "Archimedes' Principle", "Continuity Equation"],
                  "correctIndex": 1,
                  "concept": "Pascal's Law",
                  "explanation": "Pascal's Law describes pressure transmission in confined fluids.",
                  "questionFormat": "MATCHING",
                  "questionGroup": "group-1"
                }
                """,
                QuizItem.class
        );

        assertThat(item.questionFormat()).isEqualTo("MATCHING");
        assertThat(item.questionGroup()).isEqualTo("group-1");
        assertThat(item.correctIndex()).isEqualTo(1);
    }

    @Test
    void equalityIncludesWorkingSolution() {
        QuizItem first = new QuizItem(
                "What is the power?",
                List.of("5 W", "10 W", "20 W", "25 W"),
                1,
                "Electrical power",
                "Power is voltage multiplied by current.",
                null,
                "COMPUTATIONAL",
                "P = IV = 5 × 2 = 10 W"
        );
        QuizItem same = new QuizItem(
                "What is the power?",
                List.of("5 W", "10 W", "20 W", "25 W"),
                1,
                "Electrical power",
                "Power is voltage multiplied by current.",
                null,
                "COMPUTATIONAL",
                "P = IV = 5 × 2 = 10 W"
        );
        QuizItem differentWorkingSolution = new QuizItem(
                "What is the power?",
                List.of("5 W", "10 W", "20 W", "25 W"),
                1,
                "Electrical power",
                "Power is voltage multiplied by current.",
                null,
                "COMPUTATIONAL",
                "P = IV = 10 × 1 = 10 W"
        );

        assertThat(first).isEqualTo(same);
        assertThat(first).isNotEqualTo(differentWorkingSolution);
    }

    @Test
    void equalityIncludesQuestionFormat() {
        QuizItem mcq = new QuizItem(
                "Ohm's Law states that voltage is directly proportional to current.",
                List.of("True", "False"),
                null,
                "Ohm's Law",
                "Ohm's Law relates voltage, current, and resistance.",
                "A",
                "MCQ",
                null,
                null
        );
        QuizItem trueFalse = new QuizItem(
                "Ohm's Law states that voltage is directly proportional to current.",
                List.of("True", "False"),
                null,
                "Ohm's Law",
                "Ohm's Law relates voltage, current, and resistance.",
                "A",
                "TRUE_FALSE",
                null,
                null
        );

        assertThat(mcq).isNotEqualTo(trueFalse);
        assertThat(mcq.hashCode()).isNotEqualTo(trueFalse.hashCode());
    }

    @Test
    void equalityIncludesCorrectIndices() {
        QuizItem first = new QuizItem(
                "Which properties describe acids?",
                List.of("Donate protons", "Taste bitter", "Turn blue litmus red", "Release hydroxide ions"),
                null,
                "Acids",
                "Acids donate protons and turn blue litmus red.",
                null,
                "MULTI_SELECT",
                null,
                null,
                List.of(0, 2)
        );
        QuizItem differentCorrectIndices = new QuizItem(
                "Which properties describe acids?",
                List.of("Donate protons", "Taste bitter", "Turn blue litmus red", "Release hydroxide ions"),
                null,
                "Acids",
                "Acids donate protons and turn blue litmus red.",
                null,
                "MULTI_SELECT",
                null,
                null,
                List.of(0, 3)
        );

        assertThat(first).isNotEqualTo(differentCorrectIndices);
        assertThat(first.hashCode()).isNotEqualTo(differentCorrectIndices.hashCode());
    }

    @Test
    void equalityIncludesQuestionGroup() {
        QuizItem first = new QuizItem(
                "Pressure applied to a confined fluid is transmitted equally.",
                List.of("Bernoulli's Principle", "Pascal's Law", "Archimedes' Principle", "Continuity Equation"),
                1,
                "Pascal's Law",
                "Pascal's Law describes pressure transmission in confined fluids.",
                null,
                "MATCHING",
                null,
                null,
                null,
                "group-1"
        );
        QuizItem differentGroup = new QuizItem(
                "Pressure applied to a confined fluid is transmitted equally.",
                List.of("Bernoulli's Principle", "Pascal's Law", "Archimedes' Principle", "Continuity Equation"),
                1,
                "Pascal's Law",
                "Pascal's Law describes pressure transmission in confined fluids.",
                null,
                "MATCHING",
                null,
                null,
                null,
                "group-2"
        );

        assertThat(first).isNotEqualTo(differentGroup);
        assertThat(first.hashCode()).isNotEqualTo(differentGroup.hashCode());
    }

    @Test
    void equalityIncludesKeyConcept() {
        QuizItem first = new QuizItem(
                "Which stage generates most ATP?",
                List.of("Glycolysis", "Citric acid cycle", "Electron transport chain", "Fermentation"),
                2,
                "Energy systems",
                "The electron transport chain produces most ATP.",
                null,
                "MCQ",
                null,
                null,
                null,
                null,
                "Electron transport chain",
                null,
                null
        );
        QuizItem differentKeyConcept = new QuizItem(
                "Which stage generates most ATP?",
                List.of("Glycolysis", "Citric acid cycle", "Electron transport chain", "Fermentation"),
                2,
                "Energy systems",
                "The electron transport chain produces most ATP.",
                null,
                "MCQ",
                null,
                null,
                null,
                null,
                "ATP synthesis",
                null,
                null
        );

        assertThat(first).isNotEqualTo(differentKeyConcept);
        assertThat(first.hashCode()).isNotEqualTo(differentKeyConcept.hashCode());
    }

    @Test
    void equalityIncludesAcceptableAnswers() {
        QuizItem first = identificationItem(List.of("Ohm's Law", "Ohms law"));
        QuizItem differentAcceptableAnswers = identificationItem(List.of("Kirchhoff's Law"));

        assertThat(first).isNotEqualTo(differentAcceptableAnswers);
        assertThat(first.hashCode()).isNotEqualTo(differentAcceptableAnswers.hashCode());
    }

    /**
     * ⚠️ A DERIVED GETTER IS NOT A READ-ONLY ADDITION. Jackson treats {@code isX()} as a property, so an
     * un-annotated {@code isMultiSelect()} writes a {@code "multiSelect"} key into EVERY serialized
     * QuizItem — widening the persisted JSONB in {@code generated_quizzes}, {@code study_packs},
     * {@code quick_review_sessions.session_state}, {@code exam_question_pool},
     * {@code challenge_quiz_question_bank} and {@code combined_quizzes}, across every quiz mode.
     * {@code answer()} carries {@code @JsonIgnore} for exactly this reason.
     *
     * <p>This pins the SERIALIZED SHAPE rather than the annotation, so it also catches a future getter
     * added without one.
     */
    @Test
    void serializedQuizItemExposesNoDerivedProperties() throws Exception {
        QuizItem item = new QuizItem("Q?", List.of("Alpha", "Bravo", "Charlie", "Delta"), 0, "Concept", "Because");

        String json = new ObjectMapper().writeValueAsString(item);

        assertThat(json).doesNotContain("multiSelect").doesNotContain("\"answer\"");
        // The real fields must still be there, so this cannot pass by serializing nothing.
        assertThat(json).contains("question").contains("choices").contains("correctIndex");
    }

    /**
     * ⚠️ THE FIXTURE MUST ALREADY LOOK LABELLED, or this proves nothing. A choice like {@code "Smith"}
     * round-trips cleanly under BOTH the defect and the fix; only text that still matches
     * {@code ^\s*[A-Da-d]\s*[.)]\s*} can tell them apart. {@code "D.C. generator"} and {@code "B. Smith"}
     * are exactly the shapes this catalog produces — Electrical notation and biology binomials.
     */
    @Test
    void storedChoicesRoundTripByteIdentically() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<String> stored = List.of("B. Smith", "D.C. generator", "A. thaliana", "Plain text");
        String json = mapper.writeValueAsString(
                QuizItem.fromStoredComponents("Q?", stored, 0, "Concept", "Because", null,
                        "MCQ", null, null, null, null, null, null, null, null));

        QuizItem once = mapper.readValue(json, QuizItem.class);
        QuizItem twice = mapper.readValue(mapper.writeValueAsString(once), QuizItem.class);
        QuizItem thrice = mapper.readValue(mapper.writeValueAsString(twice), QuizItem.class);

        // Byte-identical, and stable across REPEATED reads -- the defect compounded on each one.
        assertThat(once.choices()).containsExactlyElementsOf(stored);
        assertThat(twice.choices()).containsExactlyElementsOf(stored);
        assertThat(thrice.choices()).containsExactlyElementsOf(stored);
    }

    /**
     * ⚠️ THE GUARD AT THE OTHER END. The fix must not be achieved by weakening generation: a label arriving
     * from the LLM still has to come off EXACTLY ONCE. Without this, removing the strip entirely would pass
     * the round-trip test above while shipping "A. Ohm's Law" to learners.
     */
    @Test
    void generationStillStripsExactlyOneChoiceLabel() {
        QuizItem generated = new QuizItem(
                "Q?",
                List.of("A. B. Smith", "B. D.C. generator", "C. Plain", "D. Other"),
                0,
                "Concept",
                "Because");

        assertThat(generated.choices()).containsExactly("B. Smith", "D.C. generator", "Plain", "Other");
    }

    /**
     * The trusted rebuild used by exam-version shuffling reorders choices without re-sanitizing them.
     */
    @Test
    void shuffledChoicesAreNotReSanitized() {
        QuizItem generated = new QuizItem("Q?", List.of("A. B. Smith", "B. Other"), 0, "Concept", "Because");

        QuizItem shuffled = generated.withShuffledChoices(List.of("Other", "B. Smith"), 1, null);

        assertThat(shuffled.choices()).containsExactly("Other", "B. Smith");
    }

    private QuizItem identificationItem(List<String> acceptableAnswers) {
        return new QuizItem(
                "Identify the law that relates voltage, current, and resistance.",
                List.of(),
                null,
                "Ohm's Law",
                "Ohm's Law relates voltage, current, and resistance.",
                null,
                "IDENTIFICATION",
                null,
                null,
                null,
                null,
                "Ohm's Law",
                acceptableAnswers,
                null
        );
    }
}
