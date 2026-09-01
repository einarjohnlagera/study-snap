package com.studysnap.backend.service;

import com.studysnap.backend.dto.ApplicableProgramResponse;
import com.studysnap.backend.dto.AdminNoteApplicableProgramsPageResponse;
import com.studysnap.backend.dto.NoteApplicableProgramsResponse;
import com.studysnap.backend.entity.DomainContext;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
    void oneJoinedProgramShadowsCourseProgramWithoutDomainContext() {
        UUID userId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), userId);
        ApplicableProgramResponse program = new ApplicableProgramResponse(UUID.randomUUID(), "Nursing");
        authorize(note, user(userId, UserRole.USER, ProfileType.TEACHER));
        when(noteCourseProgramRepository.findByNoteId(note.getId())).thenReturn(List.of(program));

        NoteApplicableProgramsResponse result = service.get(note.getId().toString(), userId);

        assertThat(result.programs()).containsExactly(program);
        assertThat(result.courseProgramShadowed()).isTrue();
    }

    @Test
    void multipleJoinedProgramsShadowCourseProgramWithDomainContext() {
        UUID userId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), userId);
        note.setDomainContext(DomainContext.NURSING);
        authorize(note, user(userId, UserRole.USER, ProfileType.STUDENT));
        when(noteCourseProgramRepository.findByNoteId(note.getId())).thenReturn(List.of(
                new ApplicableProgramResponse(UUID.randomUUID(), "Nursing"),
                new ApplicableProgramResponse(UUID.randomUUID(), "Pharmacy")
        ));

        NoteApplicableProgramsResponse result = service.get(note.getId().toString(), userId);

        assertThat(result.courseProgramShadowed()).isTrue();
    }

    @Test
    void domainContextAloneDoesNotShadowCourseProgramWithoutJoinedPrograms() {
        // Regression for the pressure-test finding. This previously asserted `isTrue()`, pinning the
        // defect: with zero join rows the legacy string is the ONLY value discovery can match on, so it
        // is maximally readable, not shadowed. A Domain Context suppresses it for GENERATION only, and
        // shadowing requires BOTH readers to ignore it.
        UUID userId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), userId);
        note.setDomainContext(DomainContext.ENGINEERING_SCIENCES);
        authorize(note, user(userId, UserRole.USER, ProfileType.STUDENT));
        when(noteCourseProgramRepository.findByNoteId(note.getId())).thenReturn(List.of());

        NoteApplicableProgramsResponse result = service.get(note.getId().toString(), userId);

        assertThat(result.programs()).isEmpty();
        assertThat(result.courseProgramShadowed()).isFalse();
    }

    @Test
    void midOnboardingCuratorCannotReplaceProgramsOnItsOwnNote() {
        // Nobody curates during onboarding -- the fourth curator predicate now carries the same guard as
        // the three in NoteService, NoteGenerationService and NoteBulkGenerationService.
        UUID adminId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), adminId);
        UUID programId = UUID.randomUUID();
        authorize(note, midOnboardingUser(adminId, UserRole.ADMIN, ProfileType.STUDENT));
        String noteId = note.getId().toString();
        List<UUID> requestedIds = List.of(programId);

        assertThatThrownBy(() -> service.replace(noteId, requestedIds, adminId))
                .isInstanceOf(NoteNotFoundException.class);

        verify(noteCourseProgramRepository, never()).replace(any(), any());
    }

    @Test
    void twoJoinedProgramsWithoutDomainContextDoNotShadowCourseProgram() {
        // The other branch the old predicate got wrong in the safe direction, now pinned explicitly:
        // at 2+ rows with no Domain Context, generation falls through to the string, so it is readable.
        UUID userId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), userId);
        note.setDomainContext(null);
        authorize(note, user(userId, UserRole.USER, ProfileType.STUDENT));
        when(noteCourseProgramRepository.findByNoteId(note.getId())).thenReturn(List.of(
                new ApplicableProgramResponse(UUID.randomUUID(), "Nursing"),
                new ApplicableProgramResponse(UUID.randomUUID(), "Pharmacy")
        ));

        NoteApplicableProgramsResponse result = service.get(note.getId().toString(), userId);

        assertThat(result.courseProgramShadowed()).isFalse();
    }

    @Test
    void adminCannotReplaceProgramsOnAnotherUsersNote() {
        UUID adminId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), UUID.randomUUID());
        UUID programId = UUID.randomUUID();
        authorize(note, user(adminId, UserRole.ADMIN, ProfileType.STUDENT));
        String noteId = note.getId().toString();
        List<UUID> requestedIds = List.of(programId);

        assertThatThrownBy(() -> service.replace(noteId, requestedIds, adminId))
                .isInstanceOf(NoteNotFoundException.class);

        verify(noteCourseProgramRepository, never()).replace(any(), any());
    }

    @Test
    void adminCanReplaceProgramsOnOwnedNote() {
        UUID adminId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), adminId);
        UUID programId = UUID.randomUUID();
        authorize(note, user(adminId, UserRole.ADMIN, ProfileType.STUDENT));
        when(courseProgramCatalogRepository.findExistingIds(Set.of(programId))).thenReturn(List.of(programId));
        when(noteCourseProgramRepository.findByNoteId(note.getId()))
                .thenReturn(List.of(new ApplicableProgramResponse(programId, "Pharmacy")));

        List<ApplicableProgramResponse> result = service.replace(note.getId().toString(), List.of(programId), adminId);

        assertThat(result).extracting(ApplicableProgramResponse::id).containsExactly(programId);
        verify(noteCourseProgramRepository).replace(note.getId(), new java.util.LinkedHashSet<>(List.of(programId)));
    }

    @Test
    void teacherOwnerCanReplacePrograms() {
        UUID teacherId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), teacherId);
        UUID programId = UUID.randomUUID();
        authorize(note, user(teacherId, UserRole.USER, ProfileType.TEACHER));
        when(courseProgramCatalogRepository.findExistingIds(Set.of(programId))).thenReturn(List.of(programId));
        when(noteCourseProgramRepository.findByNoteId(note.getId()))
                .thenReturn(List.of(new ApplicableProgramResponse(programId, "Nursing")));

        List<ApplicableProgramResponse> result = service.replace(note.getId().toString(), List.of(programId), teacherId);

        assertThat(result).extracting(ApplicableProgramResponse::id).containsExactly(programId);
    }

    @Test
    void teacherCannotReplaceProgramsOnAnotherTeachersNote() {
        UUID teacherId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), UUID.randomUUID());
        UUID programId = UUID.randomUUID();
        authorize(note, user(teacherId, UserRole.USER, ProfileType.TEACHER));
        String noteId = note.getId().toString();
        List<UUID> requestedIds = List.of(programId);

        assertThatThrownBy(() -> service.replace(noteId, requestedIds, teacherId))
                .isInstanceOf(NoteNotFoundException.class);
    }

    @Test
    void learnerOwnerCannotReplacePrograms() {
        UUID learnerId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), learnerId);
        UUID programId = UUID.randomUUID();
        authorize(note, user(learnerId, UserRole.USER, ProfileType.STUDENT));
        String noteId = note.getId().toString();
        List<UUID> requestedIds = List.of(programId);

        assertThatThrownBy(() -> service.replace(noteId, requestedIds, learnerId))
                .isInstanceOf(NoteNotFoundException.class);
    }

    @Test
    void noJoinedProgramsAndNoDomainContextLeavesCourseProgramReadable() {
        UUID userId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), userId);
        authorize(note, user(userId, UserRole.USER, ProfileType.STUDENT));
        when(noteCourseProgramRepository.findByNoteId(note.getId())).thenReturn(List.of());

        NoteApplicableProgramsResponse result = service.get(note.getId().toString(), userId);

        assertThat(result.programs()).isEmpty();
        assertThat(result.courseProgramShadowed()).isFalse();
    }

    @Test
    void adminCanReadAnotherUsersApplicablePrograms() {
        UUID adminId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), UUID.randomUUID());
        ApplicableProgramResponse program = new ApplicableProgramResponse(UUID.randomUUID(), "Nursing");
        authorize(note, user(adminId, UserRole.ADMIN, ProfileType.STUDENT));
        when(noteCourseProgramRepository.findByNoteId(note.getId())).thenReturn(List.of(program));

        NoteApplicableProgramsResponse result = service.get(note.getId().toString(), adminId);

        assertThat(result.programs()).containsExactly(program);
    }

    @Test
    void learnerOwnerCanReadOwnApplicablePrograms() {
        UUID learnerId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), learnerId);
        ApplicableProgramResponse program = new ApplicableProgramResponse(UUID.randomUUID(), "Nursing");
        authorize(note, user(learnerId, UserRole.USER, ProfileType.STUDENT));
        when(noteCourseProgramRepository.findByNoteId(note.getId())).thenReturn(List.of(program));

        NoteApplicableProgramsResponse result = service.get(note.getId().toString(), learnerId);

        assertThat(result.programs()).containsExactly(program);
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
    void multipleProgramsWithoutDomainContextSaveForLaterGenerationReadinessCheck() {
        UUID teacherId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), teacherId);
        UUID firstProgramId = UUID.randomUUID();
        UUID secondProgramId = UUID.randomUUID();
        authorize(note, user(teacherId, UserRole.USER, ProfileType.TEACHER));
        when(courseProgramCatalogRepository.findExistingIds(Set.of(firstProgramId, secondProgramId)))
                .thenReturn(List.of(firstProgramId, secondProgramId));

        service.replace(note.getId().toString(), List.of(firstProgramId, secondProgramId), teacherId);

        verify(noteCourseProgramRepository).replace(
                note.getId(), Set.of(firstProgramId, secondProgramId)
        );
    }

    @Test
    void adminPagePaginatesOwnedNotesInUpdatedAtDescendingOrderAndLoadsProgramsInOneQuery() {
        UUID adminId = UUID.randomUUID();
        NoteEntity note = note(UUID.randomUUID(), adminId);
        note.setTitle("Algebra");
        note.setCourseProgram("Civil Engineering");
        user(adminId, UserRole.ADMIN, ProfileType.STUDENT);
        ApplicableProgramResponse program = new ApplicableProgramResponse(UUID.randomUUID(), "Civil Engineering");
        when(noteRepository.findByOwnerUserId(eq(adminId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(note), org.springframework.data.domain.PageRequest.of(1, 1), 2));
        when(noteCourseProgramRepository.findByNoteIds(List.of(note.getId())))
                .thenReturn(Map.of(note.getId(), List.of(program)));

        AdminNoteApplicableProgramsPageResponse result = service.getAdminPage(1, 1, adminId);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.noteId()).isEqualTo(note.getId());
            assertThat(item.applicablePrograms()).containsExactly(program);
        });
        verify(noteRepository).findByOwnerUserId(eq(adminId), argThat(pageable ->
                pageable.getPageNumber() == 1
                        && pageable.getPageSize() == 1
                        && pageable.getSort().getOrderFor("updatedAt") != null
                        && pageable.getSort().getOrderFor("updatedAt").isDescending()
        ));
        verify(noteCourseProgramRepository).findByNoteIds(List.of(note.getId()));
    }

    @Test
    void adminPageReturnsAnEmptyPageWhenRequesterOwnsNoNotes() {
        UUID adminId = UUID.randomUUID();
        when(noteRepository.findByOwnerUserId(eq(adminId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 25), 0));

        AdminNoteApplicableProgramsPageResponse result = service.getAdminPage(0, 25, adminId);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isZero();
        verify(noteCourseProgramRepository).findByNoteIds(List.of());
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
        // Onboarded by default: this endpoint is not reachable mid-onboarding, so a null value would
        // model a state the surface cannot be in -- and would route every curator test down the
        // unauthorized branch. Mirrors the same correction made in NoteBulkGenerationServiceTest.
        user.setOnboardingCompletedAt(OffsetDateTime.now());
        return user;
    }

    private UserEntity midOnboardingUser(UUID userId, UserRole role, ProfileType profileType) {
        UserEntity user = user(userId, role, profileType);
        user.setOnboardingCompletedAt(null);
        return user;
    }
}
