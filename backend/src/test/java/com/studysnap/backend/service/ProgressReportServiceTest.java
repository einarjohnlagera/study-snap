package com.studysnap.backend.service;

import com.studysnap.backend.dto.ProgressReportResponse;
import com.studysnap.backend.dto.SubjectProgressEntry;
import com.studysnap.backend.entity.ConceptHealthEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.ConceptHealthRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressReportServiceTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-04T12:00:00Z");

    @Mock
    private StudyPackRepository studyPackRepository;

    @Mock
    private ConceptHealthRepository conceptHealthRepository;

    private ProgressReportService progressReportService;

    @BeforeEach
    void setUp() {
        ConceptHealthService conceptHealthService = new ConceptHealthService(null, null, null, null);
        progressReportService = new ProgressReportService(studyPackRepository, conceptHealthRepository, conceptHealthService);
    }

    @Test
    void getProgressReport_aggregatesSubjectsAndClassifiesConcepts() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity biologyPackOne = studyPack("Biology", List.of("Mitosis", "DNA"));
        StudyPackEntity biologyPackTwo = studyPack("Biology", List.of(" mitosis ", "Cells"));
        StudyPackEntity chemistryPack = studyPack("Chemistry", List.of("Bonds", "Acids"));
        StudyPackEntity emptyPack = studyPack("History", List.of());
        when(studyPackRepository.findByOwnerUserId(userId)).thenReturn(List.of(
            biologyPackOne,
            biologyPackTwo,
            chemistryPack,
            emptyPack
        ));
        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, biologyPackOne.getId())).thenReturn(List.of(
            health(biologyPackOne.getId(), "Mitosis", NOW.minusDays(1)),
            health(biologyPackOne.getId(), "DNA", NOW.minusDays(5))
        ));
        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, biologyPackTwo.getId())).thenReturn(List.of());
        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, chemistryPack.getId())).thenReturn(List.of(
            health(chemistryPack.getId(), "Bonds", NOW.minusDays(5))
        ));

        ProgressReportResponse response = progressReportService.getProgressReport(userId, NOW);

        assertThat(response.subjects()).containsExactly(
            new SubjectProgressEntry("Chemistry", 2, 0, 1, 1, 0),
            new SubjectProgressEntry("Biology", 3, 1, 1, 1, 33)
        );
        verify(conceptHealthRepository).findByUserIdAndStudyPackId(userId, biologyPackOne.getId());
        verify(conceptHealthRepository).findByUserIdAndStudyPackId(userId, biologyPackTwo.getId());
        verify(conceptHealthRepository).findByUserIdAndStudyPackId(userId, chemistryPack.getId());
    }

    @Test
    void getProgressReport_groupsBlankSubjectsUnderOtherAndSortsOtherLast() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity otherPack = studyPack(null, List.of("Orientation"));
        StudyPackEntity anatomyPack = studyPack("Anatomy", List.of("Bones"));
        when(studyPackRepository.findByOwnerUserId(userId)).thenReturn(List.of(otherPack, anatomyPack));
        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, otherPack.getId())).thenReturn(List.of());
        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, anatomyPack.getId())).thenReturn(List.of(
            health(anatomyPack.getId(), "Bones", NOW.minusDays(1))
        ));

        ProgressReportResponse response = progressReportService.getProgressReport(userId, NOW);

        assertThat(response.subjects()).containsExactly(
            new SubjectProgressEntry("Anatomy", 1, 1, 0, 0, 100),
            new SubjectProgressEntry("Other", 1, 0, 0, 1, 0)
        );
    }

    @Test
    void getProgressReport_roundsMasteryPercentageToNearestInteger() {
        UUID userId = UUID.randomUUID();
        List<String> concepts = new ArrayList<>();
        for (int index = 1; index <= 200; index++) {
            concepts.add("Concept " + index);
        }
        StudyPackEntity studyPack = studyPack("Math", concepts);
        when(studyPackRepository.findByOwnerUserId(userId)).thenReturn(List.of(studyPack));
        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPack.getId())).thenReturn(List.of(
            health(studyPack.getId(), "Concept 1", NOW.minusDays(1))
        ));

        ProgressReportResponse response = progressReportService.getProgressReport(userId, NOW);

        assertThat(response.subjects()).containsExactly(
            new SubjectProgressEntry("Math", 200, 1, 0, 199, 1)
        );
    }

    @Test
    void getProgressReport_returnsEmptyResultWhenNoPacksHaveConcepts() {
        UUID userId = UUID.randomUUID();
        when(studyPackRepository.findByOwnerUserId(userId)).thenReturn(List.of(
            studyPack("Biology", null),
            studyPack("Chemistry", List.of())
        ));

        ProgressReportResponse response = progressReportService.getProgressReport(userId, NOW);

        assertThat(response.subjects()).isEmpty();
        verifyNoInteractions(conceptHealthRepository);
    }

    private StudyPackEntity studyPack(String subject, List<String> keyConcepts) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(UUID.randomUUID());
        studyPack.setSubject(subject);
        studyPack.setKeyConcepts(keyConcepts);
        return studyPack;
    }

    private ConceptHealthEntity health(UUID studyPackId, String concept, OffsetDateTime lastCorrectAt) {
        ConceptHealthEntity health = new ConceptHealthEntity();
        health.setId(UUID.randomUUID());
        health.setStudyPackId(studyPackId);
        health.setConcept(concept);
        health.setLastCorrectAt(lastCorrectAt);
        return health;
    }
}
