package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.ChallengeQuizQuestionBankEntity;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.repository.ChallengeQuizQuestionBankRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChallengeQuizQuestionBankServiceTest {
    @Mock
    private ChallengeQuizQuestionBankRepository questionBankRepository;

    @Test
    void claimEligibleQuestions_locksAndClaimsOnlyMatchingLearnerLevelQuestions() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ChallengeQuizQuestionBankEntity first = bankedQuestion("Question one");
        ChallengeQuizQuestionBankEntity second = bankedQuestion("Question two");
        when(questionBankRepository.findClaimableForUpdate(
                userId, studyPackId, LearnerLevel.COLLEGE.name(), sessionId
        )).thenReturn(List.of(first, second));

        ChallengeQuizQuestionBankService service = new ChallengeQuizQuestionBankService(questionBankRepository);
        List<QuizItem> claimed = service.claimEligibleQuestions(
                userId, studyPackId, LearnerLevel.COLLEGE, sessionId, Set.of(first.getQuestionKey()), 2
        );

        assertThat(claimed).containsExactly(second.getQuestion());
        assertThat(first.getClaimedSessionId()).isNull();
        assertThat(second.getClaimedSessionId()).isEqualTo(sessionId);
        verify(questionBankRepository).findClaimableForUpdate(
                userId, studyPackId, LearnerLevel.COLLEGE.name(), sessionId
        );
        verify(questionBankRepository).saveAll(List.of(first, second));
    }

    @Test
    void updateOutcomesAndReleaseClaims_usesQuizCorrectnessAndClearsClaim() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ChallengeQuizQuestionBankEntity correct = bankedQuestion("Correct question");
        ChallengeQuizQuestionBankEntity incorrect = bankedQuestion("Incorrect question");
        correct.setClaimedSessionId(sessionId);
        incorrect.setClaimedSessionId(sessionId);
        when(questionBankRepository.findByUserIdAndStudyPackIdAndClaimedSessionId(userId, studyPackId, sessionId))
                .thenReturn(List.of(correct, incorrect));
        ChallengeQuizQuestionBankService service = new ChallengeQuizQuestionBankService(questionBankRepository);

        service.updateOutcomesAndReleaseClaims(
                userId,
                studyPackId,
                sessionId,
                List.of(correct.getQuestion(), incorrect.getQuestion()),
                Map.of(0, 0, 1, 1),
                Map.of(),
                Map.of(),
                Map.of()
        );

        assertThat(correct.getLastKnownOutcome()).isEqualTo("CORRECT");
        assertThat(incorrect.getLastKnownOutcome()).isEqualTo("INCORRECT");
        assertThat(correct.getClaimedSessionId()).isNull();
        assertThat(incorrect.getClaimedSessionId()).isNull();
        verify(questionBankRepository).saveAll(List.of(correct, incorrect));
    }

    @Test
    void claimEligibleQuestions_degradesToEmptyWhenRepositoryReadFails() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(questionBankRepository.findClaimableForUpdate(
                eq(userId), eq(studyPackId), eq(LearnerLevel.COLLEGE.name()), eq(sessionId)
        )).thenThrow(new RuntimeException("connection reset"));
        ChallengeQuizQuestionBankService service = new ChallengeQuizQuestionBankService(questionBankRepository);

        List<QuizItem> claimed = service.claimEligibleQuestions(
                userId, studyPackId, LearnerLevel.COLLEGE, sessionId, Set.of(), 5
        );

        assertThat(claimed).isEmpty();
    }

    @Test
    void persistGeneratedQuestions_swallowsRepositoryWriteFailure() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        doThrow(new RuntimeException("connection reset")).when(questionBankRepository).saveAll(any());
        ChallengeQuizQuestionBankService service = new ChallengeQuizQuestionBankService(questionBankRepository);

        service.persistGeneratedQuestions(
                userId, studyPackId, sessionId, LearnerLevel.COLLEGE, List.of(
                        new QuizItem("New question", List.of("A", "B", "C", "D"), 0, "Concept", "Explanation")
                )
        );

        verify(questionBankRepository).saveAll(any());
        verifyNoMoreInteractions(questionBankRepository);
    }

    private ChallengeQuizQuestionBankEntity bankedQuestion(String questionText) {
        ChallengeQuizQuestionBankEntity question = new ChallengeQuizQuestionBankEntity();
        question.setQuestionKey(questionText.toLowerCase());
        question.setQuestion(new QuizItem(questionText, List.of("A", "B", "C", "D"), 0, "Concept", "Explanation"));
        return question;
    }
}
