package com.studysnap.backend.util;

import com.studysnap.backend.dto.InterviewSourceNoteRef;
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
                        null,
                        "Photosynthesis",
                        "Review the Photosynthesis concept in your notes.",
                        "Pigment",
                        "MCQ",
                        "COMPUTATIONAL",
                        "P = IV = 5 × 2 = 10 W",
                        List.of(0, 2),
                        "group-1",
                        "Chlorophyll"
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
        assertThat(restored.getFirst().questionFormat()).isEqualTo("MCQ");
        assertThat(restored.getFirst().questionType()).isEqualTo("COMPUTATIONAL");
        assertThat(restored.getFirst().workingSolution()).isEqualTo("P = IV = 5 × 2 = 10 W");
        assertThat(restored.getFirst().correctIndices()).containsExactly(0, 2);
        assertThat(restored.getFirst().questionGroup()).isEqualTo("group-1");
        assertThat(restored.getFirst().keyConcept()).isEqualTo("Chlorophyll");
    }

    @Test
    void writeSelectedMultiChoice_andReadSelectedMultiChoices_roundTripWithoutChangingSelectedChoices() {
        List<QuizItem> quiz = List.of(
                new QuizItem(
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
                )
        );
        Map<String, Object> state = QuizSessionStateUtils.withSelectedChoice(Map.of(), 0, 1);

        Map<String, Object> updated = QuizSessionStateUtils.writeSelectedMultiChoice(state, 0, List.of(0, 2));

        assertThat(QuizSessionStateUtils.extractSelectedChoiceIndexes(updated, quiz)).containsEntry(0, 1);
        assertThat(QuizSessionStateUtils.readSelectedMultiChoices(updated, quiz)).containsEntry(0, List.of(0, 2));
    }

    @Test
    void extractQuiz_returnsEmptyWhenQuizPayloadIsMissing() {
        assertThat(QuizSessionStateUtils.extractQuiz(null)).isEmpty();
        assertThat(QuizSessionStateUtils.extractQuiz(Map.of())).isEmpty();
        assertThat(QuizSessionStateUtils.extractQuiz(Map.of("quiz", "invalid"))).isEmpty();
    }

    @Test
    void withInterviewSourceNoteRefs_andExtractInterviewSourceNoteRefs_roundTrip() {
        List<InterviewSourceNoteRef> sourceNoteRefs = List.of(
                new InterviewSourceNoteRef("study-pack-1", "note-1", "System Design", 4),
                new InterviewSourceNoteRef("study-pack-2", "note-2", "Behavioral", 3)
        );

        Map<String, Object> state = QuizSessionStateUtils.withInterviewSourceNoteRefs(
                Map.of("subMode", "INTERVIEW"),
                sourceNoteRefs
        );

        assertThat(state).containsEntry("subMode", "INTERVIEW");
        assertThat(QuizSessionStateUtils.extractInterviewSourceNoteRefs(state)).containsExactlyElementsOf(sourceNoteRefs);
    }

    @Test
    void extractInterviewSourceNoteRefs_returnsEmptyForMissingOrEmptyPayload() {
        assertThat(QuizSessionStateUtils.extractInterviewSourceNoteRefs(null)).isEmpty();
        assertThat(QuizSessionStateUtils.extractInterviewSourceNoteRefs(Map.of())).isEmpty();

        Map<String, Object> state = QuizSessionStateUtils.withInterviewSourceNoteRefs(Map.of(), List.of());

        assertThat(QuizSessionStateUtils.extractInterviewSourceNoteRefs(state)).isEmpty();
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
