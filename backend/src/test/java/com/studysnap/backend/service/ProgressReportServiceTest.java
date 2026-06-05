package com.studysnap.backend.service;

import com.studysnap.backend.dto.GoalSummaryResponse;
import com.studysnap.backend.dto.ProgressReportResponse;
import com.studysnap.backend.dto.SubjectProgressEntry;
import com.studysnap.backend.entity.ConceptHealthEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.ConceptHealthRepository;
import com.studysnap.backend.repository.NoteRepository;
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

    @Mock
    private NoteRepository noteRepository;

    private ProgressReportService progressReportService;

    @BeforeEach
    void setUp() {
        ConceptHealthService conceptHealthService = new ConceptHealthService(null, null, null, null);
        progressReportService = new ProgressReportService(
                studyPackRepository,
                conceptHealthRepository,
                conceptHealthService,
                noteRepository
        );
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

        ProgressReportResponse response = progressReportService.getProgressReport(userId, null, NOW);

        assertThat(response.subjects()).containsExactly(
            new SubjectProgressEntry("Chemistry", 2, 0, 1, 1, 0),
            new SubjectProgressEntry("Biology", 3, 1, 1, 1, 33)
        );
        assertThat(response.goalSummary()).isNull();
        assertThat(response.userCoursePrograms()).isEmpty();
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

        ProgressReportResponse response = progressReportService.getProgressReport(userId, null, NOW);

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

        ProgressReportResponse response = progressReportService.getProgressReport(userId, null, NOW);

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

        ProgressReportResponse response = progressReportService.getProgressReport(userId, null, NOW);

        assertThat(response.subjects()).isEmpty();
        verifyNoInteractions(conceptHealthRepository);
    }

    @Test
    void getProgressReport_computesAleGoalSummaryFromArchitectureNotes() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPack("Design", List.of("Site Planning", "Structures"));
        studyPack.setNoteId(noteId);
        when(studyPackRepository.findByOwnerUserId(userId)).thenReturn(List.of(studyPack));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(noteId))).thenReturn(List.of(note(noteId, "Architecture")));
        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPack.getId())).thenReturn(List.of(
                health(studyPack.getId(), "Site Planning", NOW.minusDays(1))
        ));

        ProgressReportResponse response = progressReportService.getProgressReport(userId, "ale", NOW);

        assertThat(response.goalSummary()).isEqualTo(new GoalSummaryResponse(
                "ale",
                "EXAM",
                "ALE",
                "Architect Licensure Examination",
                50,
                1,
                2,
                "Design"
        ));
    }

    @Test
    void getProgressReport_includesBothPnleCourseProgramAliasesInGoalSummary() {
        UUID userId = UUID.randomUUID();
        UUID nursingNoteId = UUID.randomUUID();
        UUID medSurgNoteId = UUID.randomUUID();
        StudyPackEntity nursingPack = studyPack("Fundamentals", List.of("Vital Signs"));
        nursingPack.setNoteId(nursingNoteId);
        StudyPackEntity medSurgPack = studyPack("Medical Surgical Nursing", List.of("Wound Care"));
        medSurgPack.setNoteId(medSurgNoteId);
        when(studyPackRepository.findByOwnerUserId(userId)).thenReturn(List.of(nursingPack, medSurgPack));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(nursingNoteId, medSurgNoteId))).thenReturn(List.of(
                note(nursingNoteId, "Nursing"),
                note(medSurgNoteId, "Medical – Surgical Nursing")
        ));
        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, nursingPack.getId())).thenReturn(List.of(
                health(nursingPack.getId(), "Vital Signs", NOW.minusDays(1))
        ));
        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, medSurgPack.getId())).thenReturn(List.of());

        ProgressReportResponse response = progressReportService.getProgressReport(userId, "pnle", NOW);

        assertThat(response.goalSummary()).isEqualTo(new GoalSummaryResponse(
                "pnle",
                "EXAM",
                "PNLE",
                "Philippine Nurse Licensure Examination",
                50,
                1,
                2,
                "Medical Surgical Nursing"
        ));
    }

    @Test
    void getProgressReport_returnsZeroGoalSummaryWhenGoalHasNoMatchingStudyPacks() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPack("Biology", List.of("Cells"));
        studyPack.setNoteId(noteId);
        when(studyPackRepository.findByOwnerUserId(userId)).thenReturn(List.of(studyPack));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(noteId))).thenReturn(List.of(note(noteId, "Biology")));
        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPack.getId())).thenReturn(List.of());

        ProgressReportResponse response = progressReportService.getProgressReport(userId, "ale", NOW);

        assertThat(response.goalSummary()).isEqualTo(new GoalSummaryResponse(
                "ale",
                "EXAM",
                "ALE",
                "Architect Licensure Examination",
                0,
                0,
                0,
                null
        ));
    }

    @Test
    void getProgressReport_picksWeakestGoalSubjectByDuePlusNotPracticedConcepts() {
        UUID userId = UUID.randomUUID();
        UUID designNoteId = UUID.randomUUID();
        UUID structuresNoteId = UUID.randomUUID();
        StudyPackEntity designPack = studyPack("Design", List.of("Site", "Codes"));
        designPack.setNoteId(designNoteId);
        StudyPackEntity structuresPack = studyPack("Structures", List.of("Loads", "Beams", "Columns"));
        structuresPack.setNoteId(structuresNoteId);
        when(studyPackRepository.findByOwnerUserId(userId)).thenReturn(List.of(designPack, structuresPack));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(designNoteId, structuresNoteId))).thenReturn(List.of(
                note(designNoteId, "Architecture"),
                note(structuresNoteId, "Architecture")
        ));
        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, designPack.getId())).thenReturn(List.of(
                health(designPack.getId(), "Site", NOW.minusDays(1)),
                health(designPack.getId(), "Codes", NOW.minusDays(1))
        ));
        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, structuresPack.getId())).thenReturn(List.of(
                health(structuresPack.getId(), "Loads", NOW.minusDays(5))
        ));

        ProgressReportResponse response = progressReportService.getProgressReport(userId, "ale", NOW);

        assertThat(response.goalSummary().weakestGoalSubject()).isEqualTo("Structures");
    }

    @Test
    void getProgressReport_computesSubjectGoalSummaryFromMatchingCourseProgramNotes() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPack("Algebra", List.of("Fractions", "Decimals"));
        studyPack.setNoteId(noteId);
        when(studyPackRepository.findByOwnerUserId(userId)).thenReturn(List.of(studyPack));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(noteId))).thenReturn(List.of(note(noteId, "Mathematics")));
        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPack.getId())).thenReturn(List.of(
                health(studyPack.getId(), "Fractions", NOW.minusDays(1))
        ));

        ProgressReportResponse response = progressReportService.getProgressReport(userId, "Mathematics", NOW);

        assertThat(response.goalSummary()).isEqualTo(new GoalSummaryResponse(
                "Mathematics",
                "SUBJECT",
                "Mathematics",
                "Mathematics",
                50,
                1,
                2,
                "Algebra"
        ));
    }

    @Test
    void getProgressReport_returnsZeroSubjectGoalSummaryWhenNoNotesMatch() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPack("Biology", List.of("Cells"));
        studyPack.setNoteId(noteId);
        when(studyPackRepository.findByOwnerUserId(userId)).thenReturn(List.of(studyPack));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(noteId))).thenReturn(List.of(note(noteId, "Biology")));
        when(conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPack.getId())).thenReturn(List.of());

        ProgressReportResponse response = progressReportService.getProgressReport(userId, "Mathematics", NOW);

        assertThat(response.goalSummary()).isEqualTo(new GoalSummaryResponse(
                "Mathematics",
                "SUBJECT",
                "Mathematics",
                "Mathematics",
                0,
                0,
                0,
                null
        ));
    }

    @Test
    void getUserCoursePrograms_returnsDistinctNonNullValuesOrderedAlphabetically() {
        UUID userId = UUID.randomUUID();
        when(noteRepository.findDistinctCourseProgramsByOwnerUserId(userId)).thenReturn(List.of("Biology", "Mathematics"));

        List<String> coursePrograms = progressReportService.getUserCoursePrograms(userId);

        assertThat(coursePrograms).containsExactly("Biology", "Mathematics");
    }

    @Test
    void getUserCoursePrograms_returnsEmptyListWhenNoNotesHaveCoursePrograms() {
        UUID userId = UUID.randomUUID();
        when(noteRepository.findDistinctCourseProgramsByOwnerUserId(userId)).thenReturn(List.of());

        List<String> coursePrograms = progressReportService.getUserCoursePrograms(userId);

        assertThat(coursePrograms).isEmpty();
    }

    private StudyPackEntity studyPack(String subject, List<String> keyConcepts) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(UUID.randomUUID());
        studyPack.setSubject(subject);
        studyPack.setKeyConcepts(keyConcepts);
        return studyPack;
    }

    private NoteEntity note(UUID id, String courseProgram) {
        NoteEntity note = new NoteEntity();
        note.setId(id);
        note.setCourseProgram(courseProgram);
        return note;
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
