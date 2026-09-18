package com.studysnap.backend.service;

import com.studysnap.backend.dto.CourseProgramCatalogItemResponse;
import com.studysnap.backend.dto.UpdateCourseProgramCatalogRequest;
import com.studysnap.backend.dto.UpdateProgramFamilyRequest;
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
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void createsAProgramFamilyWithInitialMembers() {
        UUID familyId = UUID.randomUUID();
        UUID nursingId = UUID.randomUUID();
        UUID medicineId = UUID.randomUUID();
        when(repository.findProgramFamilyByNormalizedName("health sciences")).thenReturn(Optional.empty());
        when(repository.insertProgramFamily("Health Sciences"))
                .thenReturn(new ProgramFamilyResponse(familyId, "Health Sciences"));
        when(repository.findById(nursingId)).thenReturn(Optional.of(item(nursingId, "Nursing", null, null)));
        when(repository.findById(medicineId)).thenReturn(Optional.of(item(medicineId, "Medicine", null, null)));

        service.createProgramFamily(new CreateProgramFamilyRequest(
                "Health Sciences", List.of(nursingId, medicineId, nursingId)));

        verify(repository).replaceProgramMemberships(familyId, List.of(nursingId, medicineId));
    }

    @Test
    void createFamilyWithUnknownProgramDoesNotWriteMemberships() {
        UUID familyId = UUID.randomUUID();
        UUID unknownProgramId = UUID.randomUUID();
        when(repository.findProgramFamilyByNormalizedName("health sciences")).thenReturn(Optional.empty());
        when(repository.insertProgramFamily("Health Sciences"))
                .thenReturn(new ProgramFamilyResponse(familyId, "Health Sciences"));
        when(repository.findById(unknownProgramId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createProgramFamily(
                new CreateProgramFamilyRequest("Health Sciences", List.of(unknownProgramId))))
                .isInstanceOf(CourseProgramNotFoundException.class);

        verify(repository, never()).replaceProgramMemberships(any(), any());
    }

    @Test
    void replacingFamilyMembershipUsesTheFamilyScopedWrite() {
        UUID familyId = UUID.randomUUID();
        UUID nursingId = UUID.randomUUID();
        UUID computerEngineeringId = UUID.randomUUID();
        when(repository.findProgramFamilyById(familyId))
                .thenReturn(Optional.of(new ProgramFamilyResponse(familyId, "Health Sciences")));
        when(repository.findById(nursingId)).thenReturn(Optional.of(item(nursingId, "Nursing", null, null)));
        when(repository.findById(computerEngineeringId))
                .thenReturn(Optional.of(item(computerEngineeringId, "Computer Engineering", null, null)));

        service.updateProgramFamily(familyId,
                new UpdateProgramFamilyRequest(null, List.of(nursingId, computerEngineeringId)));

        verify(repository).replaceProgramMemberships(familyId, List.of(nursingId, computerEngineeringId));
        verify(repository, never()).replaceProgramFamilies(any(), any());
    }

    @Test
    void clearingFamilyMembershipUsesAnExplicitEmptySet() {
        UUID familyId = UUID.randomUUID();
        when(repository.findProgramFamilyById(familyId))
                .thenReturn(Optional.of(new ProgramFamilyResponse(familyId, "Health Sciences")));

        service.updateProgramFamily(familyId, new UpdateProgramFamilyRequest(null, List.of()));

        verify(repository).replaceProgramMemberships(familyId, List.of());
    }

    @Test
    void omittedProgramIdsLeaveFamilyMembershipUntouched() {
        UUID familyId = UUID.randomUUID();
        when(repository.findProgramFamilyById(familyId))
                .thenReturn(Optional.of(new ProgramFamilyResponse(familyId, "Health Sciences")));

        service.updateProgramFamily(familyId, new UpdateProgramFamilyRequest("Health sciences", null));

        verify(repository, never()).replaceProgramMemberships(any(), any());
    }

    @Test
    void renamingToTheSameNameIsANoOp() {
        UUID familyId = UUID.randomUUID();
        when(repository.findProgramFamilyById(familyId))
                .thenReturn(Optional.of(new ProgramFamilyResponse(familyId, "Health Sciences")));
        when(repository.findOtherProgramFamilyByNormalizedName("health sciences", familyId))
                .thenReturn(Optional.empty());

        ProgramFamilyResponse result = service.updateProgramFamily(
                familyId, new UpdateProgramFamilyRequest("Health Sciences", null));

        assertThat(result).isEqualTo(new ProgramFamilyResponse(familyId, "Health Sciences"));
        verify(repository, never()).updateProgramFamilyName(any(), any());
    }

    @Test
    void renameToACaseVariantOfItsOwnNameIsNotAConflict() {
        UUID familyId = UUID.randomUUID();
        when(repository.findProgramFamilyById(familyId))
                .thenReturn(Optional.of(new ProgramFamilyResponse(familyId, "Health Sciences")));
        when(repository.findOtherProgramFamilyByNormalizedName("health sciences", familyId))
                .thenReturn(Optional.empty());

        ProgramFamilyResponse result = service.updateProgramFamily(
                familyId, new UpdateProgramFamilyRequest("Health sciences", null));

        assertThat(result.id()).isEqualTo(familyId);
        assertThat(result.name()).isEqualTo("Health sciences");
        verify(repository).updateProgramFamilyName(familyId, "Health sciences");
    }

    @Test
    void renameToAnExistingFamilyNameIsRejected() {
        UUID familyId = UUID.randomUUID();
        when(repository.findProgramFamilyById(familyId))
                .thenReturn(Optional.of(new ProgramFamilyResponse(familyId, "Health Sciences")));
        when(repository.findOtherProgramFamilyByNormalizedName("engineering", familyId))
                .thenReturn(Optional.of(new ProgramFamilyResponse(UUID.randomUUID(), "Engineering")));

        assertThatThrownBy(() -> service.updateProgramFamily(
                familyId, new UpdateProgramFamilyRequest(" Engineering ", null)))
                .isInstanceOf(ProgramFamilyNameConflictException.class);

        verify(repository, never()).updateProgramFamilyName(any(), any());
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
        when(repository.insert(CHEMICAL_ENGINEERING, null)).thenReturn(created.id());
        when(repository.findById(created.id())).thenReturn(Optional.of(created));

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
        when(repository.insert(normalizedName, null)).thenReturn(created.id());
        when(repository.findById(created.id())).thenReturn(Optional.of(created));

        CourseProgramCatalogItemResponse result = service.create(
                new CreateCourseProgramCatalogRequest(" K-12 ", null, null)
        );

        assertThat(result.name()).isEqualTo(normalizedName);
        verify(repository).insert(normalizedName, null);
    }

    @Test
    void createsProgramAssignedToExistingFamily() {
        UUID familyId = UUID.randomUUID();
        CourseProgramCatalogItemResponse created = item(UUID.randomUUID(), CHEMICAL_ENGINEERING, familyId, ENGINEERING);
        when(repository.findByNormalizedName("chemical engineering")).thenReturn(Optional.empty());
        when(repository.findProgramFamilyName(familyId)).thenReturn(Optional.of(ENGINEERING));
        when(repository.insert(CHEMICAL_ENGINEERING, null)).thenReturn(created.id());
        when(repository.findById(created.id())).thenReturn(Optional.of(created));

        CourseProgramCatalogItemResponse result = service.create(
                new CreateCourseProgramCatalogRequest(CHEMICAL_ENGINEERING, familyId, null)
        );

        assertThat(result.programFamilyName()).isEqualTo(ENGINEERING);
        verify(repository).insertProgramFamilies(created.id(), List.of(familyId));
    }

    @Test
    void createsProgramWithMultipleFamiliesAndDeduplicatesIds() {
        UUID firstFamilyId = UUID.randomUUID();
        UUID secondFamilyId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        CourseProgramCatalogItemResponse created = new CourseProgramCatalogItemResponse(
                programId, CHEMICAL_ENGINEERING,
                List.of(new ProgramFamilyResponse(firstFamilyId, ENGINEERING),
                        new ProgramFamilyResponse(secondFamilyId, "Computing & Technology")),
                secondFamilyId, "Computing & Technology", true);
        when(repository.findByNormalizedName("chemical engineering")).thenReturn(Optional.empty());
        when(repository.findProgramFamilyName(firstFamilyId)).thenReturn(Optional.of(ENGINEERING));
        when(repository.findProgramFamilyName(secondFamilyId)).thenReturn(Optional.of("Computing & Technology"));
        when(repository.insert(CHEMICAL_ENGINEERING, null)).thenReturn(programId);
        when(repository.findById(programId)).thenReturn(Optional.of(created));

        service.create(new CreateCourseProgramCatalogRequest(
                CHEMICAL_ENGINEERING, null, List.of(firstFamilyId, secondFamilyId, firstFamilyId), null));

        verify(repository).insertProgramFamilies(programId, List.of(firstFamilyId, secondFamilyId));
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
        verify(repository, never()).insert("civil engineering", null);
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
        verify(repository, never()).insert(CHEMICAL_ENGINEERING, "invalid");
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

        CourseProgramCatalogItemResponse result = service.updateProgramFamilies(
                programId,
                new UpdateCourseProgramCatalogRequest(List.of(familyId))
        );

        assertThat(result).isEqualTo(after);
        verify(repository).replaceProgramFamilies(programId, List.of(familyId));
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

        CourseProgramCatalogItemResponse result = service.updateProgramFamilies(
                programId,
                new UpdateCourseProgramCatalogRequest(List.of(newFamilyId))
        );

        assertThat(result).isEqualTo(after);
        verify(repository).replaceProgramFamilies(programId, List.of(newFamilyId));
    }

    @Test
    void clearsAProgramsFamilyMembership() {
        UUID programId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        CourseProgramCatalogItemResponse before = item(programId, CHEMICAL_ENGINEERING, familyId, ENGINEERING);
        CourseProgramCatalogItemResponse after = item(programId, CHEMICAL_ENGINEERING, null, null);
        when(repository.findById(programId)).thenReturn(Optional.of(before), Optional.of(after));

        CourseProgramCatalogItemResponse result = service.updateProgramFamilies(
                programId,
                new UpdateCourseProgramCatalogRequest(List.of())
        );

        assertThat(result).isEqualTo(after);
        verify(repository).replaceProgramFamilies(programId, List.of());
        verify(repository, never()).findProgramFamilyName(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void leavesIsActiveUntouchedWhenTheFieldIsOmitted() {
        UUID programId = UUID.randomUUID();
        CourseProgramCatalogItemResponse inactive = new CourseProgramCatalogItemResponse(
                programId, CHEMICAL_ENGINEERING, List.of(), null, null, false);
        when(repository.findById(programId)).thenReturn(Optional.of(inactive), Optional.of(inactive));

        CourseProgramCatalogItemResponse result = service.updateProgramFamilies(
                programId, new UpdateCourseProgramCatalogRequest(List.of(), null));

        assertThat(result.isActive()).isFalse();
        verify(repository, never()).updateIsActive(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void appliesIsActiveWithoutChangingMembershipsWhenOnlyTheFlagIsPresent() {
        UUID programId = UUID.randomUUID();
        CourseProgramCatalogItemResponse active = item(programId, CHEMICAL_ENGINEERING, null, null);
        CourseProgramCatalogItemResponse inactive = new CourseProgramCatalogItemResponse(
                programId, CHEMICAL_ENGINEERING, List.of(), null, null, false);
        when(repository.findById(programId)).thenReturn(Optional.of(active), Optional.of(inactive));

        CourseProgramCatalogItemResponse result = service.updateProgramFamilies(
                programId, new UpdateCourseProgramCatalogRequest(null, false));

        assertThat(result.isActive()).isFalse();
        verify(repository).updateIsActive(programId, false);
        verify(repository, never()).replaceProgramFamilies(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsAnUnknownProgramBeforeValidatingOrWriting() {
        UUID programId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        when(repository.findById(programId)).thenReturn(Optional.empty());
        UpdateCourseProgramCatalogRequest request = new UpdateCourseProgramCatalogRequest(List.of(familyId));

        assertThatThrownBy(() -> service.updateProgramFamilies(programId, request))
                .isInstanceOf(CourseProgramNotFoundException.class);

        verify(repository, never()).findProgramFamilyName(familyId);
        verify(repository, never()).replaceProgramFamilies(programId, List.of(familyId));
    }

    @Test
    void rejectsAnUnknownFamilyBeforeWriting() {
        UUID programId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        when(repository.findById(programId))
                .thenReturn(Optional.of(item(programId, CHEMICAL_ENGINEERING, null, null)));
        when(repository.findProgramFamilyName(familyId)).thenReturn(Optional.empty());
        UpdateCourseProgramCatalogRequest request = new UpdateCourseProgramCatalogRequest(List.of(familyId));

        assertThatThrownBy(() -> service.updateProgramFamilies(programId, request))
                .isInstanceOf(UnknownProgramFamilyException.class);

        verify(repository, never()).replaceProgramFamilies(programId, List.of(familyId));
    }

    @Test
    void keepsReadsReadOnlyAndOverridesCreateWithWritableTransaction() throws NoSuchMethodException {
        Transactional classTransaction = CourseProgramCatalogService.class.getAnnotation(Transactional.class);
        Method createMethod = CourseProgramCatalogService.class.getMethod("create", CreateCourseProgramCatalogRequest.class);
        Transactional createTransaction = createMethod.getAnnotation(Transactional.class);
        Method updateMethod = CourseProgramCatalogService.class.getMethod(
                "updateProgramFamilies",
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
