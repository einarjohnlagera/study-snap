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
                "Electron transport chain"
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
                "ATP synthesis"
        );

        assertThat(first).isNotEqualTo(differentKeyConcept);
        assertThat(first.hashCode()).isNotEqualTo(differentKeyConcept.hashCode());
    }
}
