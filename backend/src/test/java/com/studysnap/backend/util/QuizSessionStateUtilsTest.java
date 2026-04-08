package com.studysnap.backend.util;

import com.studysnap.backend.dto.QuizItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuizSessionStateUtilsTest {

    @Test
    void withQuiz_andExtractQuiz_roundTripPreservesQuizAndBaseState() {
        List<QuizItem> quiz = List.of(
                new QuizItem(
                        "What is chlorophyll?",
                        List.of("Pigment", "Protein", "Sugar", "Lipid"),
                        "Pigment",
                        "Photosynthesis",
                        "Review the Photosynthesis concept in your notes."
                )
        );

        Map<String, Object> state = QuizSessionStateUtils.withQuiz(
                quiz,
                Map.of("timeLimitSeconds", 600)
        );

        assertThat(state).containsEntry("timeLimitSeconds", 600);
        List<QuizItem> restored = QuizSessionStateUtils.extractQuiz(state);
        assertThat(restored).hasSize(1);
        assertThat(restored.getFirst().question()).isEqualTo("What is chlorophyll?");
        assertThat(restored.getFirst().choices()).containsExactly("Pigment", "Protein", "Sugar", "Lipid");
        assertThat(restored.getFirst().answer()).isEqualTo("Pigment");
        assertThat(restored.getFirst().concept()).isEqualTo("Photosynthesis");
    }

    @Test
    void extractQuiz_returnsEmptyWhenQuizPayloadIsMissing() {
        assertThat(QuizSessionStateUtils.extractQuiz(null)).isEmpty();
        assertThat(QuizSessionStateUtils.extractQuiz(Map.of())).isEmpty();
        assertThat(QuizSessionStateUtils.extractQuiz(Map.of("quiz", "invalid"))).isEmpty();
    }

    @Test
    void extractQuiz_supportsLegacyAnswerIndexPayloads() {
        Map<String, Object> state = Map.of(
                "quiz",
                List.of(
                        Map.of(
                                "question", "What is chlorophyll?",
                                "choices", List.of("Pigment", "Protein", "Sugar", "Lipid"),
                                "answerIndex", 0,
                                "concept", "Photosynthesis",
                                "explanation", "Review the Photosynthesis concept in your notes."
                        )
                )
        );

        List<QuizItem> restored = QuizSessionStateUtils.extractQuiz(state);

        assertThat(restored).hasSize(1);
        assertThat(restored.getFirst().correctIndex()).isZero();
        assertThat(restored.getFirst().answer()).isEqualTo("Pigment");
    }

    @Test
    void extractQuiz_stripsLegacyChoiceLabelsAndKeepsLetterAnswerMapping() {
        Map<String, Object> state = Map.of(
                "quiz",
                List.of(
                        Map.of(
                                "question", "Which concept hides implementation details?",
                                "choices", List.of("A. Encapsulation", "B) Abstraction", "C. Inheritance", "D) Polymorphism"),
                                "answer", "A)",
                                "concept", "OOP",
                                "explanation", "Encapsulation hides implementation details."
                        )
                )
        );

        List<QuizItem> restored = QuizSessionStateUtils.extractQuiz(state);

        assertThat(restored).hasSize(1);
        assertThat(restored.getFirst().choices())
                .containsExactly("Encapsulation", "Abstraction", "Inheritance", "Polymorphism");
        assertThat(restored.getFirst().correctIndex()).isZero();
        assertThat(restored.getFirst().answer()).isEqualTo("Encapsulation");
    }
}
