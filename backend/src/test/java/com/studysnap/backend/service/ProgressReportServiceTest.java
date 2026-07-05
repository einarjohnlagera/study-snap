package com.studysnap.backend.service;

import com.studysnap.backend.dto.GoalSummaryResponse;
import com.studysnap.backend.dto.ProgressReportResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.SubjectProgressEntry;
import com.studysnap.backend.entity.ConceptHealthEntity;
import com.studysnap.backend.entity.MemorizationCardEntity;
import com.studysnap.backend.entity.MemorizationGrade;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.model.StudyPackProgressProjection;
import com.studysnap.backend.model.StudyPackProgressView;
import com.studysnap.backend.repository.ConceptHealthRepository;
import com.studysnap.backend.repository.MemorizationCardRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressReportServiceTest {
    private static final String WHITESPACE = "  ";
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
        when(studyPackRepository.findProgressViewsByOwnerUserId(userId)).thenReturn(asProjections(
            biologyPackOne,
            biologyPackTwo,
            chemistryPack,
            emptyPack
        ));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(
                userId,
                List.of(biologyPackOne.getId(), biologyPackTwo.getId())
        )).thenReturn(List.of(
                health(biologyPackOne.getId(), "Mitosis", NOW.minusDays(1)),
                health(biologyPackOne.getId(), "DNA", NOW.minusDays(5))
        ));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(chemistryPack.getId())))
                .thenReturn(List.of(health(chemistryPack.getId(), "Bonds", NOW.minusDays(5))));

        ProgressReportResponse response = progressReportService.getProgressReport(userId, null, NOW);

        assertThat(response.subjects()).containsExactly(
            new SubjectProgressEntry("Chemistry", 2, 0, 1, 1, 0),
            new SubjectProgressEntry("Biology", 3, 1, 1, 1, 33)
        );
        assertThat(response.goalSummary()).isNull();
        assertThat(response.userCoursePrograms()).isEmpty();
        verify(conceptHealthRepository).findByUserIdAndStudyPackIdIn(
                userId,
                List.of(biologyPackOne.getId(), biologyPackTwo.getId())
        );
        verify(conceptHealthRepository).findByUserIdAndStudyPackIdIn(userId, List.of(chemistryPack.getId()));
    }

    @Test
    void getProgressReport_groupsBlankSubjectsUnderOtherAndSortsOtherLast() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity otherPack = studyPack(null, List.of("Orientation"));
        StudyPackEntity anatomyPack = studyPack("Anatomy", List.of("Bones"));
        when(studyPackRepository.findProgressViewsByOwnerUserId(userId)).thenReturn(asProjections(otherPack, anatomyPack));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(otherPack.getId()))).thenReturn(List.of());
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(anatomyPack.getId())))
                .thenReturn(List.of(health(anatomyPack.getId(), "Bones", NOW.minusDays(1))));

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
        when(studyPackRepository.findProgressViewsByOwnerUserId(userId)).thenReturn(asProjections(studyPack));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(studyPack.getId())))
                .thenReturn(List.of(health(studyPack.getId(), "Concept 1", NOW.minusDays(1))));

        ProgressReportResponse response = progressReportService.getProgressReport(userId, null, NOW);

        assertThat(response.subjects()).containsExactly(
            new SubjectProgressEntry("Math", 200, 1, 0, 199, 1)
        );
    }

    @Test
    void getProgressReport_returnsEmptyResultWhenNoPacksHaveConcepts() {
        UUID userId = UUID.randomUUID();
        when(studyPackRepository.findProgressViewsByOwnerUserId(userId)).thenReturn(asProjections(
            studyPack("Biology", null),
            studyPack("Chemistry", List.of())
        ));

        ProgressReportResponse response = progressReportService.getProgressReport(userId, null, NOW);

        assertThat(response.subjects()).isEmpty();
        verifyNoInteractions(conceptHealthRepository);
    }

    @Test
    void buildSubjectProgressEntries_reusesProgressClassificationForProvidedPacks() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity biologyPack = studyPack("Biology", List.of("Cells", "DNA"));
        StudyPackEntity otherPack = studyPack(WHITESPACE, List.of("Orientation"));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(biologyPack.getId(), otherPack.getId())))
                .thenReturn(List.of(
                        health(biologyPack.getId(), "Cells", NOW.minusDays(1)),
                        health(biologyPack.getId(), "DNA", NOW.minusDays(5))
                ));

        List<SubjectProgressEntry> subjects = progressReportService.buildSubjectProgressEntries(
                List.of(biologyPack, otherPack),
                userId,
                NOW
        );

        assertThat(subjects).containsExactly(
                new SubjectProgressEntry("Biology", 2, 1, 1, 0, 50),
                new SubjectProgressEntry("Other", 1, 0, 0, 1, 0)
        );
    }

    @Test
    void getConceptCountsPerStudyPack_batchesHealthLookupAndClassifiesEachPack() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity firstPack = studyPack("Biology", List.of("Cells", "DNA", "Mitosis"));
        StudyPackEntity secondPack = studyPack("Chemistry", List.of("Bonds", "Acids"));
        List<UUID> studyPackIds = List.of(firstPack.getId(), secondPack.getId());
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, studyPackIds)).thenReturn(List.of(
                health(firstPack.getId(), "Cells", NOW.minusDays(1)),
                health(firstPack.getId(), "DNA", NOW.minusDays(5)),
                health(secondPack.getId(), "Bonds", NOW.minusDays(1))
        ));

        Map<UUID, ProgressReportService.ConceptCounts> result = progressReportService.getConceptCountsPerStudyPack(
                studyPackIds,
                List.of(firstPack, secondPack),
                userId,
                NOW
        );

        assertThat(result).containsEntry(firstPack.getId(), new ProgressReportService.ConceptCounts(3, 1, 1, 1));
        assertThat(result).containsEntry(secondPack.getId(), new ProgressReportService.ConceptCounts(2, 1, 0, 1));
        verify(conceptHealthRepository, times(1)).findByUserIdAndStudyPackIdIn(userId, studyPackIds);
        verify(conceptHealthRepository, never()).findByUserIdAndStudyPackIdIn(userId, List.of(firstPack.getId()));
        verify(conceptHealthRepository, never()).findByUserIdAndStudyPackIdIn(userId, List.of(secondPack.getId()));
    }

    @Test
    void getConceptCountsPerStudyPack_returnsZeroEntryForPackWithNoConcepts() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity emptyPack = studyPack("Biology", List.of());
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(emptyPack.getId())))
                .thenReturn(List.of());

        Map<UUID, ProgressReportService.ConceptCounts> result = progressReportService.getConceptCountsPerStudyPack(
                List.of(emptyPack.getId()),
                List.of(emptyPack),
                userId,
                NOW
        );

        assertThat(result).containsEntry(emptyPack.getId(), new ProgressReportService.ConceptCounts(0, 0, 0, 0));
    }

    @Test
    void getConceptCountsPerStudyPack_skipsHealthLookupWhenStudyPackIdsAreEmpty() {
        UUID userId = UUID.randomUUID();

        Map<UUID, ProgressReportService.ConceptCounts> result = progressReportService.getConceptCountsPerStudyPack(
                List.of(),
                List.of(),
                userId,
                NOW
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(conceptHealthRepository);
    }

    @Test
    void getConceptCountsPerStudyPack_matchesFullEntityWhenLargeColumnsAreOmittedFromProjection() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity fullPack = studyPack("Biology", List.of("Cells", "DNA", "Mitosis"));
        fullPack.setOwnerUserId(userId);
        fullPack.setNoteId(noteId);
        fullPack.setStatus(StudyPackStatus.DONE);
        fullPack.setSourceText("large source text");
        fullPack.setQuiz(List.of(new QuizItem(
                "Which structure stores genetic information?",
                List.of("DNA", "Ribosome"),
                0,
                "DNA",
                "DNA carries genetic instructions."
        )));
        StudyPackProgressView projection = new TestStudyPackProgressView(
                fullPack.getId(),
                fullPack.getNoteId(),
                fullPack.getOwnerUserId(),
                fullPack.getSubject(),
                fullPack.getKeyConcepts(),
                fullPack.getStatus()
        );
        List<UUID> studyPackIds = List.of(fullPack.getId());
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, studyPackIds)).thenReturn(List.of(
                health(fullPack.getId(), "Cells", NOW.minusDays(1)),
                health(fullPack.getId(), "DNA", NOW.minusDays(5))
        ));

        Map<UUID, ProgressReportService.ConceptCounts> fullEntityCounts = progressReportService.getConceptCountsPerStudyPack(
                studyPackIds,
                List.of(fullPack),
                userId,
                NOW
        );
        Map<UUID, ProgressReportService.ConceptCounts> projectionCounts = progressReportService.getConceptCountsPerStudyPack(
                studyPackIds,
                List.of(projection),
                userId,
                NOW
        );

        assertThat(projectionCounts).isEqualTo(fullEntityCounts);
        assertThat(projectionCounts).containsEntry(fullPack.getId(), new ProgressReportService.ConceptCounts(3, 1, 1, 1));
        verify(conceptHealthRepository, times(2)).findByUserIdAndStudyPackIdIn(userId, studyPackIds);
    }

    @Test
    void buildSubjectProgressEntries_ignoresMemorizationCardsForReadinessFirewall() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPack("Biology", List.of("Cells", "DNA"));
        MemorizationCardEntity memorizationCard = new MemorizationCardEntity();
        memorizationCard.setId(UUID.randomUUID());
        memorizationCard.setUserId(userId);
        memorizationCard.setStudyPackId(studyPack.getId());
        memorizationCard.setConcept("cells");
        memorizationCard.setIntervalDays(30);
        memorizationCard.setRepetitions(10);
        memorizationCard.setDueAt(NOW.plusDays(30));
        memorizationCard.setLastGrade(MemorizationGrade.EASY);
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(studyPack.getId())))
                .thenReturn(List.of(health(studyPack.getId(), "Cells", NOW.minusDays(1))));

        List<SubjectProgressEntry> subjects = progressReportService.buildSubjectProgressEntries(
                List.of(studyPack),
                userId,
                NOW
        );

        assertThat(memorizationCard.getIntervalDays()).isEqualTo(30);
        assertThat(subjects).containsExactly(new SubjectProgressEntry("Biology", 2, 1, 0, 1, 50));
        assertThat(Arrays.stream(ProgressReportService.class.getDeclaredFields()).map(java.lang.reflect.Field::getType))
                .doesNotContain(MemorizationCardRepository.class);
        verify(conceptHealthRepository).findByUserIdAndStudyPackIdIn(userId, List.of(studyPack.getId()));
    }

    @Test
    void getProgressReport_computesAleGoalSummaryFromArchitectureNotes() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPack("Design", List.of("Site Planning", "Structures"));
        studyPack.setNoteId(noteId);
        when(studyPackRepository.findProgressViewsByOwnerUserId(userId)).thenReturn(asProjections(studyPack));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(noteId))).thenReturn(List.of(note(noteId, "Architecture")));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(studyPack.getId()))).thenReturn(List.of(
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
                1,
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
        when(studyPackRepository.findProgressViewsByOwnerUserId(userId)).thenReturn(asProjections(nursingPack, medSurgPack));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(nursingNoteId, medSurgNoteId))).thenReturn(List.of(
                note(nursingNoteId, "Nursing"),
                note(medSurgNoteId, "Medical – Surgical Nursing")
        ));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(nursingPack.getId()))).thenReturn(List.of(
                health(nursingPack.getId(), "Vital Signs", NOW.minusDays(1))
        ));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(medSurgPack.getId()))).thenReturn(List.of());
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(
                userId,
                List.of(nursingPack.getId(), medSurgPack.getId())
        )).thenReturn(List.of(health(nursingPack.getId(), "Vital Signs", NOW.minusDays(1))));

        ProgressReportResponse response = progressReportService.getProgressReport(userId, "pnle", NOW);

        assertThat(response.goalSummary()).isEqualTo(new GoalSummaryResponse(
                "pnle",
                "EXAM",
                "PNLE",
                "Philippine Nurse Licensure Examination",
                50,
                1,
                2,
                1,
                "Medical Surgical Nursing"
        ));
    }

    @Test
    void getProgressReport_returnsZeroGoalSummaryWhenGoalHasNoMatchingStudyPacks() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPack("Biology", List.of("Cells"));
        studyPack.setNoteId(noteId);
        when(studyPackRepository.findProgressViewsByOwnerUserId(userId)).thenReturn(asProjections(studyPack));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(noteId))).thenReturn(List.of(note(noteId, "Biology")));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(studyPack.getId()))).thenReturn(List.of());

        ProgressReportResponse response = progressReportService.getProgressReport(userId, "ale", NOW);

        assertThat(response.goalSummary()).isEqualTo(new GoalSummaryResponse(
                "ale",
                "EXAM",
                "ALE",
                "Architect Licensure Examination",
                0,
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
        when(studyPackRepository.findProgressViewsByOwnerUserId(userId)).thenReturn(asProjections(designPack, structuresPack));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(designNoteId, structuresNoteId))).thenReturn(List.of(
                note(designNoteId, "Architecture"),
                note(structuresNoteId, "Architecture")
        ));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(designPack.getId()))).thenReturn(List.of(
                health(designPack.getId(), "Site", NOW.minusDays(1)),
                health(designPack.getId(), "Codes", NOW.minusDays(1))
        ));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(structuresPack.getId()))).thenReturn(List.of(
                health(structuresPack.getId(), "Loads", NOW.minusDays(5))
        ));

        ProgressReportResponse response = progressReportService.getProgressReport(userId, "ale", NOW);

        assertThat(response.goalSummary().weakestGoalSubject()).isEqualTo("Structures");
    }

    @Test
    void getProgressReport_includesGoalNotPracticedConceptsWhenGoalHasMixedConceptStates() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPack("Algebra", List.of("Fractions", "Decimals", "Whole Numbers"));
        studyPack.setNoteId(noteId);
        when(studyPackRepository.findProgressViewsByOwnerUserId(userId)).thenReturn(asProjections(studyPack));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(noteId))).thenReturn(List.of(note(noteId, "Mathematics")));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(studyPack.getId()))).thenReturn(List.of(
                health(studyPack.getId(), "Fractions", NOW.minusDays(1)),
                health(studyPack.getId(), "Decimals", NOW.minusDays(5))
        ));

        ProgressReportResponse response = progressReportService.getProgressReport(userId, "Mathematics", NOW);

        assertThat(response.goalSummary()).isNotNull();
        assertThat(response.goalSummary().masteredConcepts()).isEqualTo(1);
        assertThat(response.goalSummary().notPracticedConcepts()).isEqualTo(1);
        assertThat(response.goalSummary().masteryPercentage()).isEqualTo(33);
    }

    @Test
    void getProgressReport_computesSubjectGoalSummaryFromMatchingCourseProgramNotes() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPack("Algebra", List.of("Fractions", "Decimals"));
        studyPack.setNoteId(noteId);
        when(studyPackRepository.findProgressViewsByOwnerUserId(userId)).thenReturn(asProjections(studyPack));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(noteId))).thenReturn(List.of(note(noteId, "Mathematics")));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(studyPack.getId()))).thenReturn(List.of(
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
                1,
                "Algebra"
        ));
    }

    @Test
    void getProgressReport_computesSubjectFocusGoalSummaryFromSelectedSubject() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity pharmacologyPack = studyPack("Pharmacology", List.of("Beta Blockers", "ACE Inhibitors"));
        StudyPackEntity anatomyPack = studyPack("Anatomy", List.of("Bones"));
        when(studyPackRepository.findProgressViewsByOwnerUserId(userId)).thenReturn(asProjections(pharmacologyPack, anatomyPack));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(pharmacologyPack.getId()))).thenReturn(List.of(
                health(pharmacologyPack.getId(), "Beta Blockers", NOW.minusDays(1))
        ));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(anatomyPack.getId()))).thenReturn(List.of());

        ProgressReportResponse response = progressReportService.getProgressReport(
                userId,
                null,
                List.of("Pharmacology"),
                NOW
        );

        assertThat(response.goalSummary()).isEqualTo(new GoalSummaryResponse(
                "Pharmacology",
                "SUBJECT_FOCUS",
                "Pharmacology",
                "Pharmacology",
                50,
                1,
                2,
                1,
                "Pharmacology"
        ));
    }

    @Test
    void getProgressReport_aggregatesMultipleSubjectFocusSubjects() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity pharmacologyPack = studyPack("Pharmacology", List.of("Beta Blockers", "ACE Inhibitors"));
        StudyPackEntity anatomyPack = studyPack("Anatomy", List.of("Bones"));
        StudyPackEntity chemistryPack = studyPack("Chemistry", List.of("Bonds"));
        when(studyPackRepository.findProgressViewsByOwnerUserId(userId)).thenReturn(asProjections(pharmacologyPack, anatomyPack, chemistryPack));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(pharmacologyPack.getId()))).thenReturn(List.of(
                health(pharmacologyPack.getId(), "Beta Blockers", NOW.minusDays(1))
        ));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(anatomyPack.getId()))).thenReturn(List.of(
                health(anatomyPack.getId(), "Bones", NOW.minusDays(5))
        ));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(chemistryPack.getId()))).thenReturn(List.of());
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(
                userId,
                List.of(pharmacologyPack.getId(), anatomyPack.getId())
        )).thenReturn(List.of(
                health(pharmacologyPack.getId(), "Beta Blockers", NOW.minusDays(1)),
                health(anatomyPack.getId(), "Bones", NOW.minusDays(5))
        ));

        ProgressReportResponse response = progressReportService.getProgressReport(
                userId,
                null,
                List.of("Pharmacology", "Anatomy"),
                NOW
        );

        assertThat(response.goalSummary()).isEqualTo(new GoalSummaryResponse(
                "Pharmacology, Anatomy",
                "SUBJECT_FOCUS",
                "2 subjects in focus",
                "2 subjects in focus",
                33,
                1,
                3,
                1,
                "Anatomy"
        ));
    }

    @Test
    void getProgressReport_returnsNullGoalSummaryWhenSubjectFocusIsEmpty() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity pharmacologyPack = studyPack("Pharmacology", List.of("Beta Blockers"));
        when(studyPackRepository.findProgressViewsByOwnerUserId(userId)).thenReturn(asProjections(pharmacologyPack));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(pharmacologyPack.getId()))).thenReturn(List.of());

        ProgressReportResponse response = progressReportService.getProgressReport(userId, null, List.of(), NOW);

        assertThat(response.goalSummary()).isNull();
    }

    @Test
    void getProgressReport_usesStudyGoalBeforeSubjectFocus() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPack("Design", List.of("Site Planning"));
        studyPack.setNoteId(noteId);
        when(studyPackRepository.findProgressViewsByOwnerUserId(userId)).thenReturn(asProjections(studyPack));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(noteId))).thenReturn(List.of(note(noteId, "Architecture")));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(studyPack.getId()))).thenReturn(List.of());

        ProgressReportResponse response = progressReportService.getProgressReport(
                userId,
                "ale",
                List.of("Pharmacology"),
                NOW
        );

        assertThat(response.goalSummary()).isNotNull();
        assertThat(response.goalSummary().goalType()).isEqualTo("EXAM");
        assertThat(response.goalSummary().goalName()).isEqualTo("ALE");
    }

    @Test
    void getProgressReport_returnsZeroSubjectGoalSummaryWhenNoNotesMatch() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPack("Biology", List.of("Cells"));
        studyPack.setNoteId(noteId);
        when(studyPackRepository.findProgressViewsByOwnerUserId(userId)).thenReturn(asProjections(studyPack));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(noteId))).thenReturn(List.of(note(noteId, "Biology")));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(studyPack.getId()))).thenReturn(List.of());

        ProgressReportResponse response = progressReportService.getProgressReport(userId, "Mathematics", NOW);

        assertThat(response.goalSummary()).isEqualTo(new GoalSummaryResponse(
                "Mathematics",
                "SUBJECT",
                "Mathematics",
                "Mathematics",
                0,
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

    @Test
    void buildGoalNudge_reusesGoalAggregationAndReturnsDueConcepts() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = studyPack("Algebra", List.of("Fractions", "Decimals"));
        studyPack.setNoteId(noteId);
        when(studyPackRepository.findProgressViewsByOwnerUserId(userId)).thenReturn(asProjections(studyPack));
        when(noteRepository.findByOwnerUserIdAndIdIn(userId, List.of(noteId))).thenReturn(List.of(note(noteId, "Mathematics")));
        when(conceptHealthRepository.findByUserIdAndStudyPackIdIn(userId, List.of(studyPack.getId()))).thenReturn(List.of(
                health(studyPack.getId(), "Fractions", NOW.minusDays(1)),
                health(studyPack.getId(), "Decimals", NOW.minusDays(5))
        ));

        var nudge = progressReportService.buildGoalNudge(userId, "Mathematics", NOW);

        assertThat(nudge.studyGoal()).isEqualTo("Mathematics");
        assertThat(nudge.goalType()).isEqualTo("SUBJECT");
        assertThat(nudge.goalName()).isEqualTo("Mathematics");
        assertThat(nudge.masteryPercentage()).isEqualTo(50);
        assertThat(nudge.dueConcepts()).isEqualTo(1);
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

    // Test double for the JPA-proxy Spring Data generates for StudyPackRepository's projection
    // queries. Deliberately does NOT reuse StudyPackEntity for this: only Spring Data's own proxy
    // is allowed to implement StudyPackProgressProjection (see StudyPackProgressProjection's
    // Javadoc) — mocks stand in for what the query actually returns.
    private static List<StudyPackProgressProjection> asProjections(StudyPackEntity... packs) {
        return java.util.Arrays.stream(packs)
                .map(pack -> (StudyPackProgressProjection) new TestStudyPackProgressView(
                        pack.getId(),
                        pack.getNoteId(),
                        pack.getOwnerUserId(),
                        pack.getSubject(),
                        pack.getKeyConcepts(),
                        pack.getStatus()
                ))
                .toList();
    }

    private record TestStudyPackProgressView(
            UUID id,
            UUID noteId,
            UUID ownerUserId,
            String subject,
            List<String> keyConcepts,
            StudyPackStatus status
    ) implements StudyPackProgressProjection {
        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public UUID getNoteId() {
            return noteId;
        }

        @Override
        public UUID getOwnerUserId() {
            return ownerUserId;
        }

        @Override
        public String getSubject() {
            return subject;
        }

        @Override
        public List<String> getKeyConcepts() {
            return keyConcepts;
        }

        @Override
        public StudyPackStatus getStatus() {
            return status;
        }
    }
}
