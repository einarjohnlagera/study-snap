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
}
