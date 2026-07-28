package com.studysnap.backend.service;

import com.studysnap.backend.dto.CreatorImpactResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.NoteCopyCountProjection;
import com.studysnap.backend.repository.NoteLearnersHelpedProjection;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PublicNoteEventCountProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatorImpactServiceTest {
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private AnalyticsEventRepository analyticsEventRepository;

    private CreatorImpactService creatorImpactService;

    @BeforeEach
    void setUp() {
        creatorImpactService = new CreatorImpactService(noteRepository, analyticsEventRepository);
    }

    @Test
    void getMine_usesCreatorLevelDistinctCountInsteadOfSummingPerNoteCounts() {
        UUID creatorUserId = UUID.randomUUID();
        NoteEntity firstNote = publicNote(UUID.randomUUID(), "Cell Biology");
        NoteEntity secondNote = publicNote(UUID.randomUUID(), "Human Anatomy");
        List<UUID> noteIds = List.of(firstNote.getId(), secondNote.getId());
        when(noteRepository.findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(
                creatorUserId,
                NoteVisibility.PUBLIC
        )).thenReturn(List.of(firstNote, secondNote));
        when(noteRepository.countDistinctLearnersHelpedBySourceNoteIds(noteIds)).thenReturn(List.of(
                learnerProjection(firstNote.getId(), 1),
                learnerProjection(secondNote.getId(), 1)
        ));
        when(noteRepository.countDistinctLearnersHelpedByCreatorUserId(creatorUserId)).thenReturn(1L);
        when(noteRepository.countCopiedPublicNotesBySourceNoteIds(noteIds)).thenReturn(List.of(
                copyProjection(firstNote.getId(), 4),
                copyProjection(secondNote.getId(), 2)
        ));
        when(analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(
                AnalyticsEventType.PUBLIC_NOTE_VIEWED,
                noteIds
        )).thenReturn(List.of(
                eventProjection(firstNote.getId(), 12),
                eventProjection(secondNote.getId(), 7)
        ));

        CreatorImpactResponse response = creatorImpactService.getMine(creatorUserId);

        assertThat(response.distinctLearnersHelped()).isEqualTo(1);
        assertThat(response.notes()).extracting(CreatorImpactResponse.NoteImpact::distinctLearnersHelped)
                .containsExactly(1L, 1L);
        assertThat(response.notes()).extracting(CreatorImpactResponse.NoteImpact::viewCount)
                .containsExactly(12L, 7L);
        assertThat(response.notes()).extracting(CreatorImpactResponse.NoteImpact::copyCount)
                .containsExactly(4L, 2L);
    }

    @Test
    void getMine_returnsAnEmptyImpactResponseWhenCreatorHasNoPublicNotes() {
        UUID creatorUserId = UUID.randomUUID();
        when(noteRepository.findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(
                creatorUserId,
                NoteVisibility.PUBLIC
        )).thenReturn(List.of());

        CreatorImpactResponse response = creatorImpactService.getMine(creatorUserId);

        assertThat(response.distinctLearnersHelped()).isZero();
        assertThat(response.notes()).isEmpty();
        verify(noteRepository, never()).countDistinctLearnersHelpedByCreatorUserId(creatorUserId);
    }

    private NoteEntity publicNote(UUID noteId, String title) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setTitle(title);
        return note;
    }

    private NoteLearnersHelpedProjection learnerProjection(UUID noteId, long learnerCount) {
        return new NoteLearnersHelpedProjection() {
            @Override
            public UUID getNoteId() {
                return noteId;
            }

            @Override
            public long getLearnerCount() {
                return learnerCount;
            }
        };
    }

    private NoteCopyCountProjection copyProjection(UUID noteId, long copyCount) {
        return new NoteCopyCountProjection() {
            @Override
            public UUID getNoteId() {
                return noteId;
            }

            @Override
            public long getCopyCount() {
                return copyCount;
            }
        };
    }

    private PublicNoteEventCountProjection eventProjection(UUID noteId, long totalCount) {
        return new PublicNoteEventCountProjection() {
            @Override
            public UUID getNoteId() {
                return noteId;
            }

            @Override
            public long getTotalCount() {
                return totalCount;
            }
        };
    }
}
