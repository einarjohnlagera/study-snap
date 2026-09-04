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

    /**
     * ⚠️ AT THE LAYER THE DEFECT LIVES AT. Shuffling rebuilt each item through the PUBLIC constructor, which
     * re-ran the non-idempotent choice-label strip — so generating a second exam version silently truncated
     * its choices. A test that exercises {@code QuizItem.withShuffledChoices} directly does NOT catch this:
     * it passes while this class still rebuilds the wrong way (verified by mutation).
     *
     * <p>⚠️ The fixture text must ALREADY look labelled, or it survives both the defect and the fix.
     */
    @Test
    void shuffleQuestionsAndChoices_doesNotReStripChoiceLabels() {
        List<String> labelLooking = List.of("B. Smith", "D.C. generator", "A. thaliana", "Plain");
        QuizItem item = QuizItem.fromStoredComponents(
                "Which one?", labelLooking, 0, "Concept", "Because", null,
                "MCQ", null, null, null, null, null, null, null, null);

        List<QuizItem> shuffled = QuizVersionShuffleUtils.shuffleQuestionsAndChoices(List.of(item), "B", "seed-1");

        assertThat(shuffled).hasSize(1);
        // Order may change; the TEXT must not.
        assertThat(shuffled.getFirst().choices()).containsExactlyInAnyOrderElementsOf(labelLooking);
    }

    @Test
    void shuffleQuestionsAndChoices_keepsCorrectChoiceIndexAlignedToShuffledChoices() {
        QuizItem question = new QuizItem(
                "Which answer is correct?",
                List.of("Distractor A", "Correct", "Distractor B", "Distractor C"),
                1,
                "Versioning",
                "The correct choice must follow the shuffled text.",
                null,
                "MCQ",
                null,
                null,
                null,
                null,
                "Versioned key concept",
                null,
                null
        );

        QuizItem shuffled = QuizVersionShuffleUtils.shuffleQuestionsAndChoices(List.of(question), "C", "quiz-2").getFirst();

        assertThat(shuffled.answer()).isEqualTo("Correct");
        assertThat(shuffled.choices().get(shuffled.correctIndex())).isEqualTo("Correct");
        assertThat(shuffled.keyConcept()).isEqualTo("Versioned key concept");
    }

    @Test
    void shuffleQuestionsAndChoices_remapsMultiSelectCorrectIndices() {
        QuizItem question = new QuizItem(
                "Which answers are correct?",
                List.of("Correct A", "Distractor A", "Correct B", "Distractor B"),
                null,
                "Versioning",
                "Both correct choices must follow the shuffled text.",
                null,
                "MULTI_SELECT",
                null,
                null,
                List.of(0, 2)
        );

        QuizItem shuffled = QuizVersionShuffleUtils.shuffleQuestionsAndChoices(List.of(question), "B", "quiz-3").getFirst();

        assertThat(shuffled.correctIndices().stream().map(shuffled.choices()::get).toList())
                .containsExactlyInAnyOrder("Correct A", "Correct B");
        assertThat(shuffled.answer()).isIn("Correct A", "Correct B");
    }

    @Test
    void shuffleQuestionsAndChoices_appliesSameChoicePermutationToMatchingGroup() {
        List<String> choices = List.of(
                "Bernoulli's Principle",
                "Pascal's Law",
                "Archimedes' Principle",
                "Continuity Equation"
        );
        List<QuizItem> questions = List.of(
                new QuizItem(
                        "Pressure applied to a confined fluid is transmitted equally.",
                        choices,
                        1,
                        "Pascal's Law",
                        "Pascal's Law describes pressure transmission in confined fluids.",
                        null,
                        "MATCHING",
                        null,
                        null,
                        null,
                        "group-1"
                ),
                new QuizItem(
                        "Buoyant force equals the weight of fluid displaced.",
                        choices,
                        2,
                        "Archimedes",
                        "Archimedes' Principle describes buoyant force.",
                        null,
                        "MATCHING",
                        null,
                        null,
                        null,
                        "group-1"
                )
        );

        List<QuizItem> shuffled = QuizVersionShuffleUtils.shuffleQuestionsAndChoices(questions, "B", "quiz-4");

        assertThat(shuffled).hasSize(2);
        assertThat(shuffled.get(0).choices()).containsExactlyElementsOf(shuffled.get(1).choices());
        assertThat(shuffled.get(0).choices().get(shuffled.get(0).correctIndex())).isEqualTo("Pascal's Law");
        assertThat(shuffled.get(1).choices().get(shuffled.get(1).correctIndex())).isEqualTo("Archimedes' Principle");
        assertThat(shuffled).extracting(QuizItem::questionGroup).containsOnly("group-1");
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
