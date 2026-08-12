package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.service.model.StudyPackQuizMastery;
import com.studysnap.backend.testutil.builders.StudyPackEntityBuilder;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudyPackQuizMasteryServiceTest {

    @Test
    void resolve_returnsMasteryAtForVerifiedPerfectQuickReview() {
        QuickReviewSessionRepository repository = mock(QuickReviewSessionRepository.class);
        StudyPackQuizMasteryService service = new StudyPackQuizMasteryService(repository);
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime masteredAt = OffsetDateTime.parse("2026-08-12T08:30:00Z");
        StudyPackEntity studyPack = studyPack(studyPackId, 5);
        when(repository.findQuizMasteredAt(userId, studyPackId, 5)).thenReturn(masteredAt);

        StudyPackQuizMastery result = service.resolve(userId, studyPack);

        assertThat(result.mastered()).isTrue();
        assertThat(result.masteredAt()).isEqualTo(masteredAt);
    }

    @Test
    void resolve_rechecksAgainstCurrentQuizSizeAfterRegeneration() {
        QuickReviewSessionRepository repository = mock(QuickReviewSessionRepository.class);
        StudyPackQuizMasteryService service = new StudyPackQuizMasteryService(repository);
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity regeneratedStudyPack = studyPack(studyPackId, 6);
        when(repository.findQuizMasteredAt(userId, studyPackId, 6)).thenReturn(null);

        StudyPackQuizMastery result = service.resolve(userId, regeneratedStudyPack);

        assertThat(result.mastered()).isFalse();
        verify(repository).findQuizMasteredAt(userId, studyPackId, 6);
        verify(repository, never()).findQuizMasteredAt(userId, studyPackId, 5);
    }

    @Test
    void resolve_returnsNotMasteredWhenLookupFails() {
        QuickReviewSessionRepository repository = mock(QuickReviewSessionRepository.class);
        StudyPackQuizMasteryService service = new StudyPackQuizMasteryService(repository);
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPack(studyPackId, 5);
        when(repository.findQuizMasteredAt(userId, studyPackId, 5))
                .thenThrow(new IllegalStateException("database unavailable"));

        StudyPackQuizMastery result = service.resolve(userId, studyPack);

        assertThat(result).isEqualTo(StudyPackQuizMastery.notMastered());
    }

    @Test
    void resolve_doesNotQueryForAnEmptyQuiz() {
        QuickReviewSessionRepository repository = mock(QuickReviewSessionRepository.class);
        StudyPackQuizMasteryService service = new StudyPackQuizMasteryService(repository);

        StudyPackQuizMastery result = service.resolve(UUID.randomUUID(), studyPack(UUID.randomUUID(), 0));

        assertThat(result).isEqualTo(StudyPackQuizMastery.notMastered());
        verify(repository, never()).findQuizMasteredAt(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private StudyPackEntity studyPack(UUID studyPackId, int quizSize) {
        List<QuizItem> quiz = java.util.stream.IntStream.range(0, quizSize)
                .mapToObj(index -> new QuizItem(
                        "Question " + index,
                        List.of("Correct", "Incorrect"),
                        0,
                        "Concept " + index,
                        "Explanation"
                ))
                .toList();
        return StudyPackEntityBuilder.aStudyPack()
                .withId(studyPackId)
                .withQuiz(quiz)
                .build();
    }
}
