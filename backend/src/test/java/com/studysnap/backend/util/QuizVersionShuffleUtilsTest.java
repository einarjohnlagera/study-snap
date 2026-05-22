package com.studysnap.backend.util;

import com.studysnap.backend.dto.QuizItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class QuizVersionShuffleUtilsTest {
    @Test
    void shuffleQuestionsAndChoices_isDeterministicPerVersion() {
        List<QuizItem> questions = buildQuestions();

        List<QuizItem> first = QuizVersionShuffleUtils.shuffleQuestionsAndChoices(questions, "A", "quiz-1");
        List<QuizItem> second = QuizVersionShuffleUtils.shuffleQuestionsAndChoices(questions, "A", "quiz-1");
        List<QuizItem> third = QuizVersionShuffleUtils.shuffleQuestionsAndChoices(questions, "B", "quiz-1");

        assertThat(second).isEqualTo(first);
        assertThat(third).isNotEqualTo(first);
    }

    @Test
    void shuffleQuestionsAndChoices_keepsCorrectChoiceIndexAlignedToShuffledChoices() {
        QuizItem question = new QuizItem(
                "Which answer is correct?",
                List.of("Distractor A", "Correct", "Distractor B", "Distractor C"),
                1,
                "Versioning",
                "The correct choice must follow the shuffled text."
        );

        QuizItem shuffled = QuizVersionShuffleUtils.shuffleQuestionsAndChoices(List.of(question), "C", "quiz-2").getFirst();

        assertThat(shuffled.answer()).isEqualTo("Correct");
        assertThat(shuffled.choices().get(shuffled.correctIndex())).isEqualTo("Correct");
    }

    private List<QuizItem> buildQuestions() {
        return IntStream.rangeClosed(1, 8)
                .mapToObj(index -> new QuizItem(
                        "Question " + index,
                        List.of("Correct " + index, "Distractor A " + index, "Distractor B " + index, "Distractor C " + index),
                        0,
                        "Topic " + index,
                        "Explanation " + index
                ))
                .toList();
    }
}
