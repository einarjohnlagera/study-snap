package com.studysnap.backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.studysnap.backend.dto.QuizItem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuizSessionReviewUtilsTest {

    @Test
    void computeFullyCorrectKeyConcepts_groupsByKeyConceptWhenPresent() {
        List<QuizItem> quiz = List.of(
                quizItem("Q1", 0, "Domain A", "Key Concept A"),
                quizItem("Q2", 1, "Domain B", "Key Concept B"),
                quizItem("Q3", 0, "Domain C", "Key Concept A")
        );
        Map<Integer, Integer> selectedChoices = Map.of(
                0, 0,
                1, 1,
                2, 0
        );

        List<String> concepts = QuizSessionReviewUtils.computeFullyCorrectKeyConcepts(
                quiz,
                selectedChoices,
                Map.of()
        );

        assertThat(concepts).containsExactly("Key Concept A", "Key Concept B");
    }

    @Test
    void computeFullyCorrectKeyConcepts_excludesKeyConceptWithAnyWrongAnswer() {
        List<QuizItem> quiz = List.of(
                quizItem("Q1", 0, "Domain A", "Key Concept A"),
                quizItem("Q2", 1, "Domain B", "Key Concept A"),
                quizItem("Q3", 2, "Domain C", "Key Concept B")
        );
        Map<Integer, Integer> selectedChoices = Map.of(
                0, 0,
                1, 0,
                2, 2
        );

        List<String> concepts = QuizSessionReviewUtils.computeFullyCorrectKeyConcepts(
                quiz,
                selectedChoices,
                Map.of()
        );

        assertThat(concepts).containsExactly("Key Concept B");
    }

    @Test
    void computeFullyCorrectKeyConcepts_fallsBackToConceptWhenKeyConceptMissing() {
        List<QuizItem> quiz = List.of(
                quizItem("Q1", 0, "Legacy Concept", null),
                quizItem("Q2", 1, "Blank Key Concept", " ")
        );
        Map<Integer, Integer> selectedChoices = Map.of(
                0, 0,
                1, 1
        );

        List<String> concepts = QuizSessionReviewUtils.computeFullyCorrectKeyConcepts(
                quiz,
                selectedChoices,
                Map.of()
        );

        assertThat(concepts).containsExactly("Legacy Concept", "Blank Key Concept");
    }

    private QuizItem quizItem(String question, int correctIndex, String concept, String keyConcept) {
        return new QuizItem(
                question,
                List.of("A", "B", "C", "D"),
                correctIndex,
                concept,
                "Explanation",
                null,
                "MCQ",
                null,
                null,
                null,
                null,
                keyConcept
        );
    }
}
