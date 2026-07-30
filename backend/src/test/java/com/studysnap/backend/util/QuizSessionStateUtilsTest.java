package com.studysnap.backend.util;

import com.studysnap.backend.dto.InterviewSourceNoteRef;
import com.studysnap.backend.dto.QuizItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
                        "Chlorophyll",
                        null,
                        null
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
    void withQuiz_andExtractQuiz_roundTripPreservesIdentificationAnswers() {
        List<QuizItem> quiz = List.of(identificationItem());

        Map<String, Object> state = QuizSessionStateUtils.withQuiz(quiz, Map.of());
        List<QuizItem> restored = QuizSessionStateUtils.extractQuiz(state);

        assertThat(restored).hasSize(1);
        assertThat(restored.getFirst().choices()).isEmpty();
        assertThat(restored.getFirst().correctIndex()).isNull();
        assertThat(restored.getFirst().questionFormat()).isEqualTo("IDENTIFICATION");
        assertThat(restored.getFirst().acceptableAnswers()).containsExactly("Ohm's Law", "Ohms law");
    }

    @Test
    void withSelectedIdentificationAnswer_andExtractSelectedIdentificationAnswers_roundTrip() {
        Map<String, Object> state = QuizSessionStateUtils.withSelectedIdentificationAnswer(Map.of(), 0, "  Ohm's Law ");

        assertThat(QuizSessionStateUtils.extractSelectedIdentificationAnswers(state, List.of(identificationItem())))
                .containsEntry(0, "Ohm's Law");
    }

    @Test
    void withQuiz_andExtractQuiz_roundTripPreservesNestedAcceptableAnswerGroups() {
        List<QuizItem> quiz = List.of(enumerationItem());

        Map<String, Object> state = QuizSessionStateUtils.withQuiz(quiz, Map.of());
        List<QuizItem> restored = QuizSessionStateUtils.extractQuiz(state);

        assertThat(restored).hasSize(1);
        assertThat(restored.getFirst().choices()).isEmpty();
        assertThat(restored.getFirst().correctIndex()).isNull();
        assertThat(restored.getFirst().questionFormat()).isEqualTo("ENUMERATION");
        assertThat(restored.getFirst().acceptableAnswerGroups())
                .containsExactly(
                        List.of("Legislative", "Legislature"),
                        List.of("Executive"),
                        List.of("Judicial", "Judiciary")
                );
    }

    @Test
    void withSelectedEnumerationAnswer_andExtractSelectedEnumerationAnswers_roundTripPreservesBlankSlots() {
        Map<String, Object> state = QuizSessionStateUtils.withSelectedEnumerationAnswer(
                Map.of(),
                0,
                List.of("Legislative", "", "Judicial")
        );

        assertThat(QuizSessionStateUtils.extractSelectedEnumerationAnswers(state, List.of(enumerationItem())))
                .containsEntry(0, List.of("Legislative", "", "Judicial"));
    }

    @Test
    void withSelectedEnumerationAnswer_removesEntryWhenAllSlotsBlank() {
        Map<String, Object> state = QuizSessionStateUtils.withSelectedEnumerationAnswer(Map.of(), 0, List.of("Legislative"));

        Map<String, Object> cleared = QuizSessionStateUtils.withSelectedEnumerationAnswer(state, 0, List.of("", "  "));

        assertThat(QuizSessionStateUtils.extractSelectedEnumerationAnswers(cleared, List.of(enumerationItem())))
                .isEmpty();
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
    void withConceptSelectionReasons_andExtractConceptSelectionReasons_roundTripAndStayQuizAligned() {
        List<String> reasons = new ArrayList<>();
        reasons.add("DUE");
        reasons.add(null);
        reasons.add("WEAK");
        Map<String, Object> state = QuizSessionStateUtils.withConceptSelectionReasons(
                Map.of("mode", "adaptive"),
                reasons
        );

        assertThat(state).containsEntry("mode", "adaptive");
        assertThat(QuizSessionStateUtils.extractConceptSelectionReasons(state, 4))
                .containsExactly("DUE", null, "WEAK", null);
        assertThat(QuizSessionStateUtils.extractConceptSelectionReasons(state, 2))
                .containsExactly("DUE", null);
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
    void withRedoMissedSource_andExtractRedoMissedSource_roundTripAndSupportStringValues() {
        Map<String, Object> state = QuizSessionStateUtils.withRedoMissedSource(
                Map.of("mode", "challenge"),
                true
        );

        assertThat(state).containsEntry("mode", "challenge");
        assertThat(QuizSessionStateUtils.extractRedoMissedSource(state)).isTrue();
        assertThat(QuizSessionStateUtils.extractRedoMissedSource(Map.of("redoMissedSource", "true"))).isTrue();
        assertThat(QuizSessionStateUtils.extractRedoMissedSource(null)).isFalse();
        assertThat(QuizSessionStateUtils.extractRedoMissedSource(Map.of())).isFalse();
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

    private QuizItem identificationItem() {
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
                List.of("Ohm's Law", "Ohms law"),
                null
        );
    }

    private QuizItem enumerationItem() {
        return new QuizItem(
                "Name the three branches of government.",
                List.of(),
                null,
                "Branches of government",
                "The three branches are legislative, executive, and judicial.",
                null,
                "ENUMERATION",
                null,
                null,
                null,
                null,
                "Branches of government",
                null,
                List.of(List.of("Legislative", "Legislature"), List.of("Executive"), List.of("Judicial", "Judiciary"))
        );
    }
}
