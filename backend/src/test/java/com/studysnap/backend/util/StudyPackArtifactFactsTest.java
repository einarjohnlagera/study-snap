package com.studysnap.backend.util;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.StudyPackStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudyPackArtifactFactsTest {

    @Test
    void quizQuestionsRequireANonEmptyList() {
        assertThat(StudyPackArtifactFacts.hasQuizQuestions(null)).isFalse();
        assertThat(StudyPackArtifactFacts.hasQuizQuestions(List.of())).isFalse();
        assertThat(StudyPackArtifactFacts.hasQuizQuestions(List.of(new QuizItem(
                "Question?", List.of("A", "B"), 0, "A", "Because."
        )))).isTrue();
    }

    @Test
    void keyConceptsRequireANonEmptyList() {
        assertThat(StudyPackArtifactFacts.hasKeyConcepts(null)).isFalse();
        assertThat(StudyPackArtifactFacts.hasKeyConcepts(List.of())).isFalse();
        assertThat(StudyPackArtifactFacts.hasKeyConcepts(List.of("Concept"))).isTrue();
    }

    @Test
    void doneRequiresTheDoneStudyPackStatus() {
        assertThat(StudyPackArtifactFacts.isDone(null)).isFalse();
        assertThat(StudyPackArtifactFacts.isDone(StudyPackStatus.NEEDS_CONFIRMATION)).isFalse();
        assertThat(StudyPackArtifactFacts.isDone(StudyPackStatus.FAILED)).isFalse();
        assertThat(StudyPackArtifactFacts.isDone(StudyPackStatus.DONE)).isTrue();
    }
}
