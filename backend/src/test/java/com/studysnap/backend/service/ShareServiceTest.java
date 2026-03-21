package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.ShareRemixResponse;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.ShareLinkRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.testutil.builders.StudyPackEntityBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock
    private ShareLinkRepository shareLinkRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private ActivityTrackingService activityTrackingService;

    private ShareService shareService;

    @BeforeEach
    void setUp() {
        shareService = new ShareService(
                shareLinkRepository,
                studyPackRepository,
                noteRepository,
                activityTrackingService
        );
        when(noteRepository.save(any(NoteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(studyPackRepository.save(any(StudyPackEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void remixSharedStudyPack_keepsOriginalTitle_andSetsAttributionForOtherUsers() {
        UUID currentUserId = UUID.randomUUID();
        UUID sourceOwnerUserId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        StudyPackEntity source = buildSharedPack("Photosynthesis", "token-1", sourceOwnerUserId, sourceNoteId);
        when(studyPackRepository.findByShareToken("token-1")).thenReturn(Optional.of(source));

        ShareRemixResponse response = shareService.remixSharedStudyPack("token-1", currentUserId);

        ArgumentCaptor<NoteEntity> noteCaptor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(noteCaptor.capture());
        NoteEntity savedNote = noteCaptor.getValue();

        ArgumentCaptor<StudyPackEntity> savedStudyPackCaptor = ArgumentCaptor.forClass(StudyPackEntity.class);
        verify(studyPackRepository).save(savedStudyPackCaptor.capture());
        StudyPackEntity savedStudyPack = savedStudyPackCaptor.getValue();

        assertThat(savedNote.getTitle()).isEqualTo("Photosynthesis");
        assertThat(savedNote.getCopiedFromNoteId()).isEqualTo(sourceNoteId);
        assertThat(savedNote.getCopiedFromUserId()).isEqualTo(sourceOwnerUserId);
        assertThat(savedNote.getCopiedFromTitle()).isEqualTo("Photosynthesis");
        assertThat(savedNote.getCopiedFromPublic()).isTrue();
        assertThat(savedNote.getCopiedAt()).isNotNull();

        assertThat(savedStudyPack.getTitle()).isEqualTo("Photosynthesis");
        assertThat(savedStudyPack.getSummary()).isEqualTo(source.getSummary());
        assertThat(savedStudyPack.getKeyConcepts()).containsExactlyElementsOf(source.getKeyConcepts());
        assertThat(savedStudyPack.getQuiz()).containsExactlyElementsOf(source.getQuiz());
        assertThat(response.studyPackId()).isEqualTo(savedStudyPack.getId().toString());
        assertThat(response.noteId()).isEqualTo(savedNote.getId().toString());
        verify(activityTrackingService).recordActivity(currentUserId, ActivityType.CREATED_STUDY_PACK, savedStudyPack.getId());
    }

    @Test
    void remixSharedStudyPack_forOwnerDoesNotSetAttributionFields() {
        UUID ownerUserId = UUID.randomUUID();
        UUID sourceNoteId = UUID.randomUUID();
        StudyPackEntity source = buildSharedPack("Photosynthesis", "token-owner", ownerUserId, sourceNoteId);
        when(studyPackRepository.findByShareToken("token-owner")).thenReturn(Optional.of(source));

        shareService.remixSharedStudyPack("token-owner", ownerUserId);

        ArgumentCaptor<NoteEntity> noteCaptor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(noteCaptor.capture());
        NoteEntity savedNote = noteCaptor.getValue();
        assertThat(savedNote.getTitle()).isEqualTo("Photosynthesis");
        assertThat(savedNote.getCopiedFromUserId()).isNull();
        assertThat(savedNote.getCopiedFromTitle()).isNull();
        assertThat(savedNote.getCopiedFromPublic()).isFalse();
        assertThat(savedNote.getCopiedAt()).isNull();
    }

    private StudyPackEntity buildSharedPack(String title, String token, UUID sourceOwnerUserId, UUID sourceNoteId) {
        StudyPackEntity source = StudyPackEntityBuilder.aStudyPack()
                .withTitle(title)
                .withSummary("Summary for " + title)
                .withOwnerUserId(sourceOwnerUserId)
                .build();
        source.setNoteId(sourceNoteId);
        source.setShareToken(token);
        source.setSubject("Biology");
        source.setKeyConcepts(List.of("Light reactions", "Calvin cycle"));
        source.setQuiz(List.of(
                new QuizItem(
                        "What does chlorophyll absorb?",
                        List.of("Light", "Water", "Carbon dioxide", "Oxygen"),
                        "Light",
                        "Photosynthesis",
                        "Chlorophyll captures light energy."
                )
        ));
        source.setTags(new String[]{"biology", "plants"});
        return source;
    }
}
