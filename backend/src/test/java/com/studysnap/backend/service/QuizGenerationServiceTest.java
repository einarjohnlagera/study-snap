package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.service.model.GeneratedChallengeQuizContent;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizGenerationServiceTest {

    @Mock
    private LlmStudyPackService llmStudyPackService;

    @Test
    void generateChallengeQuiz_defaultsToRealProviderUntilMockModeIsExplicitlyEnabled() {
        StudySnapProperties properties = new StudySnapProperties();
        QuizGenerationService service = new QuizGenerationService(llmStudyPackService, properties);
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Engineering",
                "Physics",
                List.of("Ohm's Law")
        );
        GeneratedChallengeQuizContent llmQuiz = new GeneratedChallengeQuizContent(
                List.of(new QuizItem("Q1", List.of("A", "B", "C", "D"), 0, "Concept", "Explanation")),
                "gpt-4.1-mini",
                100,
                50,
                25
        );

        when(llmStudyPackService.generateChallengeQuiz(
                "Pack title",
                "Summary",
                List.of("Concept"),
                List.of("Existing question"),
                10,
                "medium",
                context
        )).thenReturn(llmQuiz);

        GeneratedChallengeQuizContent response = service.generateChallengeQuiz(
                "Pack title",
                "Summary",
                List.of("Concept"),
                List.of("Existing question"),
                10,
                "medium",
                context
        );

        assertThat(response).isEqualTo(llmQuiz);
        verify(llmStudyPackService).generateChallengeQuiz(
                "Pack title",
                "Summary",
                List.of("Concept"),
                List.of("Existing question"),
                10,
                "medium",
                context
        );
    }

    @Test
    void generateAdaptivePracticeQuiz_defaultsToRealProviderUntilMockModeIsExplicitlyEnabled() {
        StudySnapProperties properties = new StudySnapProperties();
        QuizGenerationService service = new QuizGenerationService(llmStudyPackService, properties);
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.BOARD_EXAM_REVIEW,
                "Nursing",
                "Medical-Surgical Nursing",
                List.of("Electrolytes")
        );
        List<QuizItem> llmQuiz = List.of(
                new QuizItem("Q1", List.of("A", "B", "C", "D"), 0, "Concept", "Explanation")
        );

        when(llmStudyPackService.generateAdaptivePracticeQuiz(
                "Pack title",
                "Summary",
                List.of("Concept"),
                List.of("Weak Concept"),
                List.of("Existing question"),
                7,
                context
        )).thenReturn(llmQuiz);

        List<QuizItem> response = service.generateAdaptivePracticeQuiz(
                "Pack title",
                "Summary",
                List.of("Concept"),
                List.of("Weak Concept"),
                List.of("Existing question"),
                7,
                context
        );

        assertThat(response).isEqualTo(llmQuiz);
        verify(llmStudyPackService).generateAdaptivePracticeQuiz(
                "Pack title",
                "Summary",
                List.of("Concept"),
                List.of("Weak Concept"),
                List.of("Existing question"),
                7,
                context
        );
    }

    @Test
    void generateChallengeQuiz_mockModeReturnsCompatibleQuizWithoutCallingLlm() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getQuizGeneration().setMode("mock");
        QuizGenerationService service = new QuizGenerationService(llmStudyPackService, properties);
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Engineering",
                "Physics",
                List.of("Electric Circuits")
        );

        GeneratedChallengeQuizContent response = service.generateChallengeQuiz(
                "Electric Circuits Midterm Review",
                "Summary",
                List.of("Ohm's Law", "Voltage", "Resistance"),
                List.of("Existing question"),
                12,
                "mixed",
                context
        );

        assertCompatibleQuiz(response.quizItems(), 12);
        assertThat(response.quizItems()).extracting(QuizItem::concept).contains("Ohm's Law", "Voltage", "Resistance");
        assertThat(response.modelUsed()).isNull();
        assertThat(response.inputTokens()).isNull();
        assertThat(response.outputTokens()).isNull();
        assertThat(response.cachedInputTokens()).isNull();
        verify(llmStudyPackService, never()).generateChallengeQuiz(
                "Electric Circuits Midterm Review",
                "Summary",
                List.of("Ohm's Law", "Voltage", "Resistance"),
                List.of("Existing question"),
                12,
                "mixed",
                context
        );
    }

    @Test
    void generateAdaptivePracticeQuiz_mockModeReturnsCompatibleQuizWithoutCallingLlm() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getQuizGeneration().setMode("mock");
        QuizGenerationService service = new QuizGenerationService(llmStudyPackService, properties);
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.BOARD_EXAM_REVIEW,
                "Nursing",
                "Medical-Surgical Nursing",
                List.of("Electrolytes")
        );

        List<QuizItem> response = service.generateAdaptivePracticeQuiz(
                "Electrolyte Imbalance Review",
                "Summary",
                List.of("Sodium", "Potassium"),
                List.of("Electrolyte Imbalance", "Fluid Shift"),
                List.of("Existing question"),
                7,
                context
        );

        assertCompatibleQuiz(response, 7);
        assertThat(response).extracting(QuizItem::concept).contains("Electrolyte Imbalance", "Fluid Shift");
        verify(llmStudyPackService, never()).generateAdaptivePracticeQuiz(
                "Electrolyte Imbalance Review",
                "Summary",
                List.of("Sodium", "Potassium"),
                List.of("Electrolyte Imbalance", "Fluid Shift"),
                List.of("Existing question"),
                7,
                context
        );
    }

    private void assertCompatibleQuiz(List<QuizItem> quizItems, int expectedCount) {
        assertThat(quizItems).hasSize(expectedCount);
        assertThat(quizItems).allSatisfy(item -> {
            assertThat(item.question()).isNotBlank();
            assertThat(item.choices()).hasSize(4).doesNotContainNull();
            assertThat(item.correctIndex()).isNotNull().isBetween(0, 3);
            assertThat(item.concept()).isNotBlank();
            assertThat(item.explanation()).isNotBlank();
        });
    }
}
