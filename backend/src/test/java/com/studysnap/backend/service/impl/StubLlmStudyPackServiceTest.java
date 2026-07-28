package com.studysnap.backend.service.impl;

import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.dto.CompanionSection;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.service.model.CompanionGenerationContext;
import com.studysnap.backend.service.model.GeneratedChallengeQuizContent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StubLlmStudyPackServiceTest {

    private final StubLlmStudyPackService service = new StubLlmStudyPackService();

    @Test
    void generateCompanion_returnsOnlyRequestedSections() {
        CompanionContent content = service.generateCompanion(
                new CompanionGenerationContext("Board Prep", null, null, List.of(), List.of()),
                Set.of(CompanionSection.FAQ)
        );

        assertThat(content.overview()).isNull();
        assertThat(content.studyStrategy()).isNull();
        assertThat(content.commonMistakes()).isNull();
        assertThat(content.resources()).isNull();
        assertThat(content.faq()).hasSize(3);
        assertThat(content.mentorTips()).isEmpty();
    }

    @Test
    void generateChallengeQuiz_includesWellFormedIdentificationAndEnumerationItems() {
        GeneratedChallengeQuizContent generated = service.generateChallengeQuiz(
                "Study Pack",
                "Summary",
                List.of("Photosynthesis", "Cell Division"),
                List.of(),
                10,
                "medium",
                null
        );
        List<QuizItem> quiz = generated.quizItems();

        QuizItem identificationItem = quiz.get(0);
        assertThat(identificationItem.questionFormat()).isEqualTo("IDENTIFICATION");
        assertThat(identificationItem.choices()).isEmpty();
        assertThat(identificationItem.correctIndex()).isNull();
        assertThat(identificationItem.acceptableAnswers()).containsExactly("Photosynthesis");
        assertThat(identificationItem.acceptableAnswerGroups()).isEmpty();

        QuizItem enumerationItem = quiz.get(1);
        assertThat(enumerationItem.questionFormat()).isEqualTo("ENUMERATION");
        assertThat(enumerationItem.choices()).isEmpty();
        assertThat(enumerationItem.correctIndex()).isNull();
        assertThat(enumerationItem.acceptableAnswers()).isEmpty();
        assertThat(enumerationItem.acceptableAnswerGroups()).containsExactly(
                List.of("Cell Division aspect one"),
                List.of("Cell Division aspect two")
        );

        assertThat(quiz).hasSize(10);
        assertThat(quiz.subList(2, quiz.size()))
                .allSatisfy(item -> assertThat(item.questionFormat()).isNull());
        assertThat(generated.modelUsed()).isNull();
        assertThat(generated.inputTokens()).isNull();
        assertThat(generated.outputTokens()).isNull();
        assertThat(generated.cachedInputTokens()).isNull();
    }
}
