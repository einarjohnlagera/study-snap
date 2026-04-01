package com.studysnap.backend.service;

import com.studysnap.backend.dto.PublicProfileNoteResponse;
import com.studysnap.backend.dto.PublicProfileResponse;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.NoteCopyCountProjection;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicProfileServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private NoteRepository noteRepository;

    private PublicProfileService publicProfileService;

    @BeforeEach
    void setUp() {
        publicProfileService = new PublicProfileService(userRepository, noteRepository);
    }

    @Test
    void getByUserId_returnsPublicProfileWithAggregatedCopyCounts() {
        UUID userId = UUID.randomUUID();
        UUID noteOneId = UUID.randomUUID();
        UUID noteTwoId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("creator@example.com");
        user.setDisplayName("Study Buddy");
        user.setFirstName("Study");
        user.setProfileType(ProfileType.TEACHER);
        user.setRole(UserRole.USER);
        user.setPublicProfileVisible(true);

        NoteEntity noteOne = buildPublicNote(noteOneId, userId, "Plant Cells", "Biology", new String[]{"cells", "plants"});
        NoteEntity noteTwo = buildPublicNote(noteTwoId, userId, "Atomic Bonds", "Chemistry", new String[]{"atoms"});

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(noteRepository.findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(userId, NoteVisibility.PUBLIC))
                .thenReturn(List.of(noteOne, noteTwo));
        when(noteRepository.countCopiedPublicNotesBySourceNoteIds(List.of(noteOneId, noteTwoId)))
                .thenReturn(List.of(
                        projection(noteOneId, 5L),
                        projection(noteTwoId, 2L)
                ));

        PublicProfileResponse response = publicProfileService.getByUserId(userId.toString(), null);

        assertThat(response.displayName()).isEqualTo("Study Buddy");
        assertThat(response.profileType()).isEqualTo("TEACHER");
        assertThat(response.isOfficial()).isFalse();
        assertThat(response.publicProfileVisible()).isTrue();
        assertThat(response.publicNotesCount()).isEqualTo(2);
        assertThat(response.totalCopies()).isEqualTo(7);
        assertThat(response.publicNotes())
                .extracting(PublicProfileNoteResponse::noteId, PublicProfileNoteResponse::copyCount,
                    PublicProfileNoteResponse::slug)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(noteOneId.toString(), 5L, "plant-cells"),
                        org.assertj.core.groups.Tuple.tuple(noteTwoId.toString(), 2L, "atomic-bonds")
                );
    }

    @Test
    void getByUserId_returnsEmptyNotesForExistingUserWithoutPublicNotes() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("creator@example.com");
        user.setFirstName("Creator");
        user.setRole(UserRole.USER);
        user.setPublicProfileVisible(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(noteRepository.findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(userId, NoteVisibility.PUBLIC))
                .thenReturn(List.of());

        PublicProfileResponse response = publicProfileService.getByUserId(userId.toString(), null);

        assertThat(response.displayName()).isEqualTo("Creator");
        assertThat(response.publicNotesCount()).isZero();
        assertThat(response.totalCopies()).isZero();
        assertThat(response.publicNotes()).isEmpty();
    }

    @Test
    void getByUserId_marksAdminAccountsAsOfficial() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("admin@example.com");
        user.setDisplayName("Moderator Mia");
        user.setRole(UserRole.ADMIN);
        user.setPublicProfileVisible(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(noteRepository.findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(userId, NoteVisibility.PUBLIC))
                .thenReturn(List.of());

        PublicProfileResponse response = publicProfileService.getByUserId(userId.toString(), null);

        assertThat(response.displayName()).isEqualTo("Moderator Mia");
        assertThat(response.isOfficial()).isTrue();
    }

    @Test
    void getByUserId_rejectsMissingUsers() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        String id = userId.toString();
        assertThatThrownBy(() -> publicProfileService.getByUserId(id, null))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo("PUBLIC_PROFILE_NOT_FOUND");
    }

    @Test
    void getByUserId_blocksPrivateProfilesForOtherViewers() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("creator@example.com");
        user.setDisplayName("Hidden Helper");
        user.setRole(UserRole.USER);
        user.setPublicProfileVisible(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        String id = userId.toString();
        UUID uuid = UUID.randomUUID();
        assertThatThrownBy(() -> publicProfileService.getByUserId(id, uuid))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode(), Throwable::getMessage)
                .containsExactly("PUBLIC_PROFILE_PRIVATE", "This profile is private.");
    }

    @Test
    void getByUserId_allowsOwnerToViewPrivateProfile() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("creator@example.com");
        user.setDisplayName("Hidden Helper");
        user.setRole(UserRole.USER);
        user.setPublicProfileVisible(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(noteRepository.findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(userId, NoteVisibility.PUBLIC))
                .thenReturn(List.of());

        PublicProfileResponse response = publicProfileService.getByUserId(userId.toString(), userId);

        assertThat(response.displayName()).isEqualTo("Hidden Helper");
        assertThat(response.publicProfileVisible()).isFalse();
    }

    private NoteEntity buildPublicNote(UUID noteId, UUID ownerUserId, String title, String subject, String[] tags) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setOwnerUserId(ownerUserId);
        note.setTitle(title);
        note.setSubject(subject);
        note.setTags(tags);
        note.setVisibility(NoteVisibility.PUBLIC);
        note.setCreatedAt(OffsetDateTime.now().minusDays(1));
        note.setUpdatedAt(OffsetDateTime.now().minusHours(1));
        return note;
    }

    private NoteCopyCountProjection projection(UUID noteId, long copyCount) {
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
}
