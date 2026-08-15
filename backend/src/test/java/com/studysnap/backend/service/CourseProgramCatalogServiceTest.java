package com.studysnap.backend.service;

import com.studysnap.backend.dto.CourseProgramCatalogItemResponse;
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
    void keepsReadsReadOnlyAndOverridesCreateWithWritableTransaction() throws NoSuchMethodException {
        Transactional classTransaction = CourseProgramCatalogService.class.getAnnotation(Transactional.class);
        Method createMethod = CourseProgramCatalogService.class.getMethod("create", CreateCourseProgramCatalogRequest.class);
        Transactional createTransaction = createMethod.getAnnotation(Transactional.class);

        assertThat(classTransaction.readOnly()).isTrue();
        assertThat(createTransaction).isNotNull();
        assertThat(createTransaction.readOnly()).isFalse();
    }

    private CourseProgramCatalogItemResponse item(UUID id, String name, UUID familyId, String familyName) {
        return new CourseProgramCatalogItemResponse(id, name, familyId, familyName);
    }
}
