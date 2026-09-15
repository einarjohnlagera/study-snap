package com.studysnap.backend.service;

import com.studysnap.backend.dto.CourseProgramCatalogItemResponse;
import com.studysnap.backend.dto.UpdateCourseProgramCatalogRequest;
import com.studysnap.backend.exception.CourseProgramNotFoundException;
import com.studysnap.backend.exception.InvalidProgramFamilyNameException;
import com.studysnap.backend.exception.ProgramFamilyNameConflictException;
import com.studysnap.backend.dto.CreateProgramFamilyRequest;
import com.studysnap.backend.dto.ProgramFamilyResponse;
import com.studysnap.backend.dto.CreateCourseProgramCatalogRequest;
import com.studysnap.backend.exception.CourseProgramCatalogNameConflictException;
import com.studysnap.backend.exception.InvalidExamGoalSlugException;
import com.studysnap.backend.exception.UnknownProgramFamilyException;
import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseProgramCatalogServiceTest {
    private static final String CIVIL_ENGINEERING = "Civil Engineering";
    private static final String CHEMICAL_ENGINEERING = "Chemical Engineering";
    private static final String ENGINEERING = "Engineering";

    private CourseProgramCatalogRepository repository;
    private CourseProgramCatalogService service;

    @BeforeEach
    void setUp() {
        repository = mock(CourseProgramCatalogRepository.class);
        service = new CourseProgramCatalogService(repository);
    }

    @Test
    void createsAProgramFamilySoANewFamilyNoLongerNeedsAMigration() {
        UUID familyId = UUID.randomUUID();
        when(repository.findProgramFamilyByNormalizedName("health sciences")).thenReturn(Optional.empty());
        when(repository.insertProgramFamily("Health Sciences"))
                .thenReturn(new ProgramFamilyResponse(familyId, "Health Sciences"));

        ProgramFamilyResponse result = service.createProgramFamily(new CreateProgramFamilyRequest("  Health Sciences  "));

        assertThat(result.id()).isEqualTo(familyId);
        assertThat(result.name()).isEqualTo("Health Sciences");
    }

    /**
     * ⚠️ THE FIXTURE DIFFERS FROM THE STORED NAME IN CASE AND WHITESPACE ON PURPOSE. An exact-match
     * duplicate check would pass a test that submitted "Engineering" verbatim, so that fixture would
     * prove nothing about normalization -- and a second "engineering" family is exactly the mistake
     * this check exists to stop, because the authoring expansion would then offer two identical-looking
     * families.
     */
    @Test
    void rejectsAFamilyWhoseNameDiffersOnlyByCaseOrWhitespace() {
        when(repository.findProgramFamilyByNormalizedName(ENGINEERING.toLowerCase(java.util.Locale.ROOT)))
                .thenReturn(Optional.of(new ProgramFamilyResponse(UUID.randomUUID(), ENGINEERING)));

        assertThatThrownBy(() -> service.createProgramFamily(new CreateProgramFamilyRequest("  eNgInEeRiNg  ")))
                .isInstanceOf(ProgramFamilyNameConflictException.class);

        verify(repository, never()).insertProgramFamily(org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * The unique constraint can still lose a race after the pre-check passes. Resolving to the winner
     * turns a raw constraint violation into the same conflict the caller already handles.
     */
    @Test
    void resolvesAConcurrentFamilyCreateToTheWinningRow() {
        when(repository.findProgramFamilyByNormalizedName("health sciences"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new ProgramFamilyResponse(UUID.randomUUID(), "Health Sciences")));
        when(repository.insertProgramFamily("Health Sciences"))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_program_families_name"));

        assertThatThrownBy(() -> service.createProgramFamily(new CreateProgramFamilyRequest("Health Sciences")))
                .isInstanceOf(ProgramFamilyNameConflictException.class);
    }

    @Test
    void listsProgramFamiliesIncludingOnesWithNoMembers() {
        UUID emptyFamilyId = UUID.randomUUID();
        when(repository.findAllProgramFamilies())
                .thenReturn(List.of(new ProgramFamilyResponse(emptyFamilyId, "Health Sciences")));

        assertThat(service.listProgramFamilies())
                .singleElement()
                .satisfies(family -> assertThat(family.id()).isEqualTo(emptyFamilyId));
    }

    @Test
    void createsProgramWithNameOnly() {
        CourseProgramCatalogItemResponse created = item(UUID.randomUUID(), CHEMICAL_ENGINEERING, null, null);
        when(repository.findByNormalizedName("chemical engineering")).thenReturn(Optional.empty());
        when(repository.insert(CHEMICAL_ENGINEERING, null, null, null)).thenReturn(created);

        CourseProgramCatalogItemResponse result = service.create(
                new CreateCourseProgramCatalogRequest(" Chemical   Engineering ", null, null)
        );

        assertThat(result).isEqualTo(created);
    }

    @Test
    void normalizesDashInStoredNameToMatchPublicFilterChip() {
        String normalizedName = "K – 12";
        CourseProgramCatalogItemResponse created = item(UUID.randomUUID(), normalizedName, null, null);
        when(repository.findByNormalizedName("k – 12")).thenReturn(Optional.empty());
        when(repository.insert(normalizedName, null, null, null)).thenReturn(created);

        CourseProgramCatalogItemResponse result = service.create(
                new CreateCourseProgramCatalogRequest(" K-12 ", null, null)
        );

        assertThat(result.name()).isEqualTo(normalizedName);
        verify(repository).insert(normalizedName, null, null, null);
    }

    @Test
    void createsProgramAssignedToExistingFamily() {
        UUID familyId = UUID.randomUUID();
        CourseProgramCatalogItemResponse created = item(UUID.randomUUID(), CHEMICAL_ENGINEERING, familyId, ENGINEERING);
        when(repository.findByNormalizedName("chemical engineering")).thenReturn(Optional.empty());
        when(repository.findProgramFamilyName(familyId)).thenReturn(Optional.of(ENGINEERING));
        when(repository.insert(CHEMICAL_ENGINEERING, familyId, ENGINEERING, null)).thenReturn(created);

        CourseProgramCatalogItemResponse result = service.create(
                new CreateCourseProgramCatalogRequest(CHEMICAL_ENGINEERING, familyId, null)
        );

        assertThat(result.programFamilyName()).isEqualTo(ENGINEERING);
    }

    @Test
    void rejectsExactDuplicateWithConflictException() {
        when(repository.findByNormalizedName("civil engineering"))
                .thenReturn(Optional.of(item(UUID.randomUUID(), CIVIL_ENGINEERING, null, null)));
        CreateCourseProgramCatalogRequest request = new CreateCourseProgramCatalogRequest(CIVIL_ENGINEERING, null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(CourseProgramCatalogNameConflictException.class)
                .hasMessageContaining(CIVIL_ENGINEERING);
    }

    @Test
    void rejectsCaseAndWhitespaceVariantWithConflictException() {
        when(repository.findByNormalizedName("civil engineering"))
                .thenReturn(Optional.of(item(UUID.randomUUID(), CIVIL_ENGINEERING, null, null)));
        CreateCourseProgramCatalogRequest request = new CreateCourseProgramCatalogRequest("  civil   engineering ", null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(CourseProgramCatalogNameConflictException.class);
        verify(repository, never()).insert("civil engineering", null, null, null);
    }

    @Test
    void rejectsUnknownProgramFamily() {
        UUID familyId = UUID.randomUUID();
        when(repository.findByNormalizedName("chemical engineering")).thenReturn(Optional.empty());
        when(repository.findProgramFamilyName(familyId)).thenReturn(Optional.empty());
        CreateCourseProgramCatalogRequest request = new CreateCourseProgramCatalogRequest(CHEMICAL_ENGINEERING, familyId, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(UnknownProgramFamilyException.class);
    }

    @Test
    void rejectsInvalidExamGoalWithoutInserting() {
        when(repository.findByNormalizedName("chemical engineering")).thenReturn(Optional.empty());
        CreateCourseProgramCatalogRequest request = new CreateCourseProgramCatalogRequest(CHEMICAL_ENGINEERING, null, "invalid");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(InvalidExamGoalSlugException.class);
        verify(repository, never()).insert(CHEMICAL_ENGINEERING, null, null, "invalid");
    }

    @Test
    void returnsNearMatchesAndNoUnrelatedPrograms() {
        CourseProgramCatalogItemResponse civil = item(UUID.randomUUID(), CIVIL_ENGINEERING, null, null);
        when(repository.findSimilar("civil engineer")).thenReturn(List.of(civil));
        when(repository.findSimilar("nursing")).thenReturn(List.of());

        assertThat(service.findSimilar("Civil Engineer")).containsExactly(civil);
        assertThat(service.findSimilar("Nursing")).isEmpty();
    }

    @Test
    void assignsAnUnassignedProgramToAFamily() {
        UUID programId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        CourseProgramCatalogItemResponse before = item(programId, CHEMICAL_ENGINEERING, null, null);
        CourseProgramCatalogItemResponse after = item(programId, CHEMICAL_ENGINEERING, familyId, ENGINEERING);
        when(repository.findById(programId)).thenReturn(Optional.of(before), Optional.of(after));
        when(repository.findProgramFamilyName(familyId)).thenReturn(Optional.of(ENGINEERING));

        CourseProgramCatalogItemResponse result = service.updateProgramFamily(
                programId,
                new UpdateCourseProgramCatalogRequest(familyId)
        );

        assertThat(result).isEqualTo(after);
        verify(repository).updateProgramFamily(programId, familyId);
    }

    @Test
    void changesAnAssignedProgramToADifferentFamily() {
        UUID programId = UUID.randomUUID();
        UUID oldFamilyId = UUID.randomUUID();
        UUID newFamilyId = UUID.randomUUID();
        CourseProgramCatalogItemResponse before = item(programId, CHEMICAL_ENGINEERING, oldFamilyId, ENGINEERING);
        CourseProgramCatalogItemResponse after = item(programId, CHEMICAL_ENGINEERING, newFamilyId, "Health Sciences");
        when(repository.findById(programId)).thenReturn(Optional.of(before), Optional.of(after));
        when(repository.findProgramFamilyName(newFamilyId)).thenReturn(Optional.of("Health Sciences"));

        CourseProgramCatalogItemResponse result = service.updateProgramFamily(
                programId,
                new UpdateCourseProgramCatalogRequest(newFamilyId)
        );

        assertThat(result).isEqualTo(after);
        verify(repository).updateProgramFamily(programId, newFamilyId);
    }

    @Test
    void clearsAProgramsFamilyMembership() {
        UUID programId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        CourseProgramCatalogItemResponse before = item(programId, CHEMICAL_ENGINEERING, familyId, ENGINEERING);
        CourseProgramCatalogItemResponse after = item(programId, CHEMICAL_ENGINEERING, null, null);
        when(repository.findById(programId)).thenReturn(Optional.of(before), Optional.of(after));

        CourseProgramCatalogItemResponse result = service.updateProgramFamily(
                programId,
                new UpdateCourseProgramCatalogRequest(null)
        );

        assertThat(result).isEqualTo(after);
        verify(repository).updateProgramFamily(programId, null);
        verify(repository, never()).findProgramFamilyName(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsAnUnknownProgramBeforeValidatingOrWriting() {
        UUID programId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        when(repository.findById(programId)).thenReturn(Optional.empty());
        UpdateCourseProgramCatalogRequest request = new UpdateCourseProgramCatalogRequest(familyId);

        assertThatThrownBy(() -> service.updateProgramFamily(programId, request))
                .isInstanceOf(CourseProgramNotFoundException.class);

        verify(repository, never()).findProgramFamilyName(familyId);
        verify(repository, never()).updateProgramFamily(programId, familyId);
    }

    @Test
    void rejectsAnUnknownFamilyBeforeWriting() {
        UUID programId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        when(repository.findById(programId))
                .thenReturn(Optional.of(item(programId, CHEMICAL_ENGINEERING, null, null)));
        when(repository.findProgramFamilyName(familyId)).thenReturn(Optional.empty());
        UpdateCourseProgramCatalogRequest request = new UpdateCourseProgramCatalogRequest(familyId);

        assertThatThrownBy(() -> service.updateProgramFamily(programId, request))
                .isInstanceOf(UnknownProgramFamilyException.class);

        verify(repository, never()).updateProgramFamily(programId, familyId);
    }

    @Test
    void keepsReadsReadOnlyAndOverridesCreateWithWritableTransaction() throws NoSuchMethodException {
        Transactional classTransaction = CourseProgramCatalogService.class.getAnnotation(Transactional.class);
        Method createMethod = CourseProgramCatalogService.class.getMethod("create", CreateCourseProgramCatalogRequest.class);
        Transactional createTransaction = createMethod.getAnnotation(Transactional.class);
        Method updateMethod = CourseProgramCatalogService.class.getMethod(
                "updateProgramFamily",
                UUID.class,
                UpdateCourseProgramCatalogRequest.class
        );
        Transactional updateTransaction = updateMethod.getAnnotation(Transactional.class);

        assertThat(classTransaction.readOnly()).isTrue();
        assertThat(createTransaction).isNotNull();
        assertThat(createTransaction.readOnly()).isFalse();
        assertThat(updateTransaction).isNotNull();
        assertThat(updateTransaction.readOnly()).isFalse();
    }

    /**
     * ⚠️ ADDED AT THE v0.133.0 SIGNOFF, after a cold agent found that
     * {@link InvalidProgramFamilyNameException} had ZERO references anywhere in the test tree — an
     * added file with no test that executes it, the exact heuristic {@code CLAUDE.md} names.
     *
     * <p>The branch looks redundant with {@code @Size(max = 120)} on the request record, and that is
     * precisely why it was easy to skip: the annotation guards the CONTROLLER, while this guards the
     * SERVICE, which anything calling it directly reaches without validation.
     */
    @Test
    void rejectsAProgramFamilyNameLongerThanTheColumnRatherThanLettingTheInsertFail() {
        String tooLong = "x".repeat(121);
        CreateProgramFamilyRequest request = new CreateProgramFamilyRequest(tooLong);

        assertThatThrownBy(() -> service.createProgramFamily(request))
                .isInstanceOf(InvalidProgramFamilyNameException.class);

        verify(repository, never()).insertProgramFamily(org.mockito.ArgumentMatchers.anyString());
    }

    private CourseProgramCatalogItemResponse item(UUID id, String name, UUID familyId, String familyName) {
        return new CourseProgramCatalogItemResponse(id, name, familyId, familyName, true);
    }
}
