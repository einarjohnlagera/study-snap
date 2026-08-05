package com.studysnap.backend.service;

import com.studysnap.backend.dto.ApplicableProgramResponse;
import com.studysnap.backend.dto.AdminNoteApplicableProgramsPageResponse;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.DuplicateCourseProgramException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.exception.UnknownCourseProgramException;
import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import com.studysnap.backend.repository.NoteCourseProgramRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteApplicableProgramsServiceTest {
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CourseProgramCatalogRepository courseProgramCatalogRepository;
    @Mock
    private NoteCourseProgramRepository noteCourseProgramRepository;

    private NoteApplicableProgramsService service;

    @BeforeEach
    void setUp() {
        service = new NoteApplicableProgramsService(
                noteRepository,
                userRepository,
                courseProgramCatalogRepository,
                noteCourseProgramRepository
        );
    }

    @Test
    void teacherOwnerCanReadApplicablePrograms() {
        UUID userId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), userId);
        ApplicableProgramResponse program = new ApplicableProgramResponse(UUID.randomUUID(), "Nursing");
        authorize(note, user(userId, UserRole.USER, ProfileType.TEACHER));
        when(noteCourseProgramRepository.findByNoteId(note.getId())).thenReturn(List.of(program));

        assertThat(service.get(note.getId().toString(), userId)).containsExactly(program);
    }

    @Test
    void adminCanReplaceProgramsOnAnotherUsersNote() {
        UUID adminId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), UUID.randomUUID());
        UUID programId = UUID.randomUUID();
        authorize(note, user(adminId, UserRole.ADMIN, ProfileType.STUDENT));
        when(courseProgramCatalogRepository.findExistingIds(Set.of(programId))).thenReturn(List.of(programId));
        when(noteCourseProgramRepository.findByNoteId(note.getId()))
                .thenReturn(List.of(new ApplicableProgramResponse(programId, "Pharmacy")));

        List<ApplicableProgramResponse> result = service.replace(
                note.getId().toString(),
                List.of(programId),
                adminId
        );

        assertThat(result).extracting(ApplicableProgramResponse::id).containsExactly(programId);
        verify(noteCourseProgramRepository).replace(note.getId(), new java.util.LinkedHashSet<>(List.of(programId)));
    }

    @Test
    void studentOwnerIsHiddenAsNotFound() {
        UUID userId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), userId);
        authorize(note, user(userId, UserRole.USER, ProfileType.STUDENT));
        String noteId = note.getId().toString();

        assertThatThrownBy(() -> service.get(noteId, userId))
                .isInstanceOf(NoteNotFoundException.class);
    }

    @Test
    void nonOwnerTeacherIsHiddenAsNotFound() {
        UUID teacherId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), UUID.randomUUID());
        authorize(note, user(teacherId, UserRole.USER, ProfileType.TEACHER));
        String noteId = note.getId().toString();

        assertThatThrownBy(() -> service.get(noteId, teacherId))
                .isInstanceOf(NoteNotFoundException.class);
    }

    @Test
    void unknownProgramRejectsBeforeReconcile() {
        UUID teacherId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), teacherId);
        UUID unknownId = UUID.randomUUID();
        authorize(note, user(teacherId, UserRole.USER, ProfileType.TEACHER));
        when(courseProgramCatalogRepository.findExistingIds(Set.of(unknownId))).thenReturn(List.of());
        String noteId = note.getId().toString();
        List<UUID> requestedIds = List.of(unknownId);

        assertThatThrownBy(() -> service.replace(noteId, requestedIds, teacherId))
                .isInstanceOf(UnknownCourseProgramException.class);
        verify(noteCourseProgramRepository, never()).replace(any(), any());
    }

    @Test
    void duplicateProgramRejectsBeforeValidationOrReconcile() {
        UUID teacherId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), teacherId);
        UUID programId = UUID.randomUUID();
        authorize(note, user(teacherId, UserRole.USER, ProfileType.TEACHER));
        String noteId = note.getId().toString();
        List<UUID> requestedIds = List.of(programId, programId);

        assertThatThrownBy(() -> service.replace(noteId, requestedIds, teacherId))
                .isInstanceOf(DuplicateCourseProgramException.class);
        verify(courseProgramCatalogRepository, never()).findExistingIds(any());
        verify(noteCourseProgramRepository, never()).replace(any(), any());
    }

    @Test
    void adminPagePaginatesAndLoadsProgramsForThePageInOneQuery() {
        UUID ownerId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), ownerId);
        note.setTitle("Algebra");
        note.setCourseProgram("Civil Engineering");
        UserEntity owner = user(ownerId, UserRole.USER, ProfileType.TEACHER);
        ApplicableProgramResponse program = new ApplicableProgramResponse(UUID.randomUUID(), "Civil Engineering");
        when(noteRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(note), org.springframework.data.domain.PageRequest.of(1, 1), 2));
        when(noteCourseProgramRepository.findByNoteIds(List.of(note.getId())))
                .thenReturn(Map.of(note.getId(), List.of(program)));
        when(userRepository.findAllById(Set.of(ownerId))).thenReturn(List.of(owner));

        AdminNoteApplicableProgramsPageResponse result = service.getAdminPage(1, 1);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.noteId()).isEqualTo(note.getId());
            assertThat(item.ownerEmail()).isEqualTo(owner.getEmail());
            assertThat(item.applicablePrograms()).containsExactly(program);
        });
        verify(noteCourseProgramRepository).findByNoteIds(List.of(note.getId()));
    }

    private void authorize(NoteEntity note, UserEntity requester) {
        when(noteRepository.findById(note.getId())).thenReturn(Optional.of(note));
        when(userRepository.findById(requester.getId())).thenReturn(Optional.of(requester));
    }

    private NoteEntity note(UUID noteId, UUID ownerId) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setOwnerUserId(ownerId);
        note.setUpdatedAt(OffsetDateTime.now());
        return note;
    }

    private UserEntity user(UUID userId, UserRole role, ProfileType profileType) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail(userId + "@example.com");
        user.setRole(role);
        user.setProfileType(profileType);
        return user;
    }
}
