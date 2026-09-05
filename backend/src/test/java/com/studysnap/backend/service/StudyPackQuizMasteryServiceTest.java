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
        when(repository.findQuizMasteredAt(userId, studyPackId, 5, studyPack.getNoteId())).thenReturn(masteredAt);

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
        UUID noteId = regeneratedStudyPack.getNoteId();
        when(repository.findQuizMasteredAt(userId, studyPackId, 6, noteId)).thenReturn(null);

        StudyPackQuizMastery result = service.resolve(userId, regeneratedStudyPack);

        assertThat(result.mastered()).isFalse();
        verify(repository).findQuizMasteredAt(userId, studyPackId, 6, noteId);
        verify(repository, never()).findQuizMasteredAt(userId, studyPackId, 5, noteId);
    }

    /**
     * ⚠️ The note id is what carries the regeneration clock into the query. Passing null here would
     * make the repository's coalesce fall back to "always true" and silently restore the defect for
     * every pack, with the query still returning a plausible answer. Pin that it is forwarded.
     */
    @Test
    void resolve_forwardsThePacksNoteIdSoTheRegenerationClockIsApplied() {
        QuickReviewSessionRepository repository = mock(QuickReviewSessionRepository.class);
        StudyPackQuizMasteryService service = new StudyPackQuizMasteryService(repository);
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPack(studyPackId, 5, noteId);

        service.resolve(userId, studyPack);

        verify(repository).findQuizMasteredAt(userId, studyPackId, 5, noteId);
    }

    @Test
    void resolve_returnsNotMasteredWhenLookupFails() {
        QuickReviewSessionRepository repository = mock(QuickReviewSessionRepository.class);
        StudyPackQuizMasteryService service = new StudyPackQuizMasteryService(repository);
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPack(studyPackId, 5);
        when(repository.findQuizMasteredAt(userId, studyPackId, 5, studyPack.getNoteId()))
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
        verify(repository, never()).findQuizMasteredAt(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any());
    }

    private StudyPackEntity studyPack(UUID studyPackId, int quizSize) {
        return studyPack(studyPackId, quizSize, UUID.randomUUID());
    }

    private StudyPackEntity studyPack(UUID studyPackId, int quizSize, UUID noteId) {
        List<QuizItem> quiz = java.util.stream.IntStream.range(0, quizSize)
                .mapToObj(index -> new QuizItem(
                        "Question " + index,
                        List.of("Correct", "Incorrect"),
                        0,
                        "Concept " + index,
                        "Explanation"
                ))
                .toList();
        StudyPackEntity entity = StudyPackEntityBuilder.aStudyPack()
                .withId(studyPackId)
                .withQuiz(quiz)
                .build();
        entity.setNoteId(noteId);
        return entity;
    }
}
