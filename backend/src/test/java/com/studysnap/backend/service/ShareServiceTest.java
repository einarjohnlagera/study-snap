package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.ShareRemixResponse;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.NoteTargetProfileType;
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
    @Mock
    private UserRepository userRepository;

    private ShareService shareService;

    @BeforeEach
    void setUp() {
        shareService = new ShareService(
                shareLinkRepository,
                studyPackRepository,
                noteRepository,
                activityTrackingService,
                userRepository
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

    @Test
    void remixSharedStudyPack_persistsTargetProfileTypeDerivedFromTheRemixer() {
        // notes.target_profile_type is NOT NULL with no database default. This path omitted it
        // entirely until v0.83.1, so every remix 500ed on the insert. Assert the PERSISTED value:
        // asserting only that the call succeeds is the shape that let the original gap through.
        UUID remixerUserId = UUID.randomUUID();
        UUID sourceOwnerUserId = UUID.randomUUID();
        StudyPackEntity source = buildSharedPack("Kinematics", "token-derive", sourceOwnerUserId, UUID.randomUUID());
        when(studyPackRepository.findByShareToken("token-derive")).thenReturn(Optional.of(source));
        UserEntity remixer = new UserEntity();
        remixer.setId(remixerUserId);
        remixer.setProfileType(ProfileType.BOARD_EXAM);
        when(userRepository.findById(remixerUserId)).thenReturn(Optional.of(remixer));

        shareService.remixSharedStudyPack("token-derive", remixerUserId);

        ArgumentCaptor<NoteEntity> noteCaptor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(noteCaptor.capture());
        // BOARD_EXAM is the case where a derivation and a hardcoded STUDENT visibly diverge.
        assertThat(noteCaptor.getValue().getTargetProfileType()).isEqualTo(NoteTargetProfileType.BOARD_TAKER);
    }

    @Test
    void remixSharedStudyPack_persistsNonNullTargetProfileTypeWhenTheOwnerCannotBeLoaded() {
        // The column is NOT NULL, so a missing user must still yield a valid value rather than a
        // constraint violation.
        UUID remixerUserId = UUID.randomUUID();
        StudyPackEntity source = buildSharedPack("Statics", "token-missing", UUID.randomUUID(), UUID.randomUUID());
        when(studyPackRepository.findByShareToken("token-missing")).thenReturn(Optional.of(source));
        when(userRepository.findById(remixerUserId)).thenReturn(Optional.empty());

        shareService.remixSharedStudyPack("token-missing", remixerUserId);

        ArgumentCaptor<NoteEntity> noteCaptor = ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteRepository).save(noteCaptor.capture());
        assertThat(noteCaptor.getValue().getTargetProfileType()).isEqualTo(NoteTargetProfileType.STUDENT);
    }
}
