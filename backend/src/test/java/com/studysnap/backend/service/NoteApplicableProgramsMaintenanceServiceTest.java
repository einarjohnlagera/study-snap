package com.studysnap.backend.service;

import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import com.studysnap.backend.repository.NoteCourseProgramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteApplicableProgramsMaintenanceServiceTest {
    @Mock
    private CourseProgramCatalogRepository courseProgramCatalogRepository;
    @Mock
    private NoteCourseProgramRepository noteCourseProgramRepository;

    private NoteApplicableProgramsMaintenanceService service;

    @BeforeEach
    void setUp() {
        service = new NoteApplicableProgramsMaintenanceService(
                courseProgramCatalogRepository,
                noteCourseProgramRepository
        );
    }

    @Test
    void createSeedsExactlyOneResolvableProgram() {
        UUID noteId = UUID.randomUUID();
        UUID nursingId = UUID.randomUUID();
        when(courseProgramCatalogRepository.resolveIdForLegacyName("Nursing")).thenReturn(Optional.of(nursingId));

        service.seedDerivedSet(noteId, "Nursing");

        verify(noteCourseProgramRepository).insert(noteId, Set.of(nursingId));
    }

    @Test
    void createLeavesExcludedProgramWithoutJoinRow() {
        UUID noteId = UUID.randomUUID();
        when(courseProgramCatalogRepository.resolveIdForLegacyName("Biology")).thenReturn(Optional.empty());

        service.seedDerivedSet(noteId, "Biology");

        verify(noteCourseProgramRepository, never()).insert(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void derivedSingletonFollowsLegacyStringEdit() {
        UUID noteId = UUID.randomUUID();
        UUID nursingId = UUID.randomUUID();
        UUID pharmacyId = UUID.randomUUID();
        when(courseProgramCatalogRepository.resolveIdForLegacyName("Nursing")).thenReturn(Optional.of(nursingId));
        when(noteCourseProgramRepository.findIdsByNoteId(noteId)).thenReturn(Set.of(nursingId));
        when(courseProgramCatalogRepository.resolveIdForLegacyName("Pharmacy")).thenReturn(Optional.of(pharmacyId));

        assertThat(service.isDerivedSet(noteId, "Nursing")).isTrue();
        service.replaceWithDerivedSet(noteId, "Pharmacy");

        verify(noteCourseProgramRepository).replace(noteId, Set.of(pharmacyId));
    }

    @Test
    void emptySetIsDerivedWhenPreEditStringWasUnresolvable() {
        UUID noteId = UUID.randomUUID();
        UUID nursingId = UUID.randomUUID();
        when(courseProgramCatalogRepository.resolveIdForLegacyName(null)).thenReturn(Optional.empty());
        when(noteCourseProgramRepository.findIdsByNoteId(noteId)).thenReturn(Set.of());
        when(courseProgramCatalogRepository.resolveIdForLegacyName("Nursing")).thenReturn(Optional.of(nursingId));

        assertThat(service.isDerivedSet(noteId, null)).isTrue();
        service.replaceWithDerivedSet(noteId, "Nursing");

        verify(noteCourseProgramRepository).replace(noteId, Set.of(nursingId));
    }

    @Test
    void excludedValueCorrectedToCatalogValueGainsJoinRow() {
        UUID noteId = UUID.randomUUID();
        UUID nursingId = UUID.randomUUID();
        when(courseProgramCatalogRepository.resolveIdForLegacyName("Biology")).thenReturn(Optional.empty());
        when(noteCourseProgramRepository.findIdsByNoteId(noteId)).thenReturn(Set.of());
        when(courseProgramCatalogRepository.resolveIdForLegacyName("Nursing")).thenReturn(Optional.of(nursingId));

        assertThat(service.isDerivedSet(noteId, "Biology")).isTrue();
        service.replaceWithDerivedSet(noteId, "Nursing");

        verify(noteCourseProgramRepository).replace(noteId, Set.of(nursingId));
    }

    @Test
    void deliberatelyClearedSetIsCuratedWhenPreEditStringResolved() {
        UUID noteId = UUID.randomUUID();
        UUID nursingId = UUID.randomUUID();
        when(courseProgramCatalogRepository.resolveIdForLegacyName("Nursing")).thenReturn(Optional.of(nursingId));
        when(noteCourseProgramRepository.findIdsByNoteId(noteId)).thenReturn(Set.of());

        assertThat(service.isDerivedSet(noteId, "Nursing")).isFalse();
    }

    @Test
    void curatedMultiRowAndMismatchedSingletonAreNotDerived() {
        UUID noteId = UUID.randomUUID();
        UUID nursingId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        when(courseProgramCatalogRepository.resolveIdForLegacyName("Nursing")).thenReturn(Optional.of(nursingId));
        when(noteCourseProgramRepository.findIdsByNoteId(noteId))
                .thenReturn(Set.of(nursingId, otherId), Set.of(otherId));

        assertThat(service.isDerivedSet(noteId, "Nursing")).isFalse();
        assertThat(service.isDerivedSet(noteId, "Nursing")).isFalse();
    }
}
