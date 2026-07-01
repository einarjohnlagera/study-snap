package com.studysnap.backend.service;

import com.studysnap.backend.config.ExamGoalConfig;
import com.studysnap.backend.dto.GoalNudgeResponse;
import com.studysnap.backend.dto.GoalSummaryResponse;
import com.studysnap.backend.dto.ProgressReportResponse;
import com.studysnap.backend.dto.SubjectProgressEntry;
import com.studysnap.backend.entity.ConceptHealthEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.ConceptHealthRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.util.SubjectNormalizationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgressReportService {
    private static final String OTHER_SUBJECT = "Other";
    private static final String GOAL_TYPE_EXAM = "EXAM";
    private static final String GOAL_TYPE_SUBJECT = "SUBJECT";
    private static final String GOAL_TYPE_SUBJECT_FOCUS = "SUBJECT_FOCUS";

    private final StudyPackRepository studyPackRepository;
    private final ConceptHealthRepository conceptHealthRepository;
    private final ConceptHealthService conceptHealthService;
    private final NoteRepository noteRepository;

    @Transactional(readOnly = true)
    public ProgressReportResponse getProgressReport(UUID userId, String studyGoal, OffsetDateTime now) {
        return getProgressReport(userId, studyGoal, List.of(), now);
    }

    @Transactional(readOnly = true)
    public ProgressReportResponse getProgressReport(
            UUID userId,
            String studyGoal,
            List<String> focusSubjects,
            OffsetDateTime now
    ) {
        Map<String, List<StudyPackEntity>> packsBySubject = groupQualifyingPacksBySubject(userId);
        List<SubjectProgressEntry> subjects = packsBySubject.entrySet().stream()
            .map(entry -> toSubjectProgress(entry.getKey(), entry.getValue(), userId, now))
            .filter(Objects::nonNull)
            .sorted(subjectProgressComparator())
            .toList();
        return new ProgressReportResponse(
                subjects,
                buildGoalSummary(userId, normalizeGoal(studyGoal), normalizeFocusSubjects(focusSubjects), packsBySubject, now),
                getUserCoursePrograms(userId),
                null
        );
    }

    public List<String> getUserCoursePrograms(UUID userId) {
        List<String> coursePrograms = noteRepository.findDistinctCourseProgramsByOwnerUserId(userId);
        return coursePrograms == null ? List.of() : coursePrograms;
    }

    public GoalNudgeResponse buildGoalNudge(UUID userId, String studyGoal, OffsetDateTime now) {
        String normalizedGoal = normalizeGoal(studyGoal);
        if (normalizedGoal == null) {
            return null;
        }

        Map<String, List<StudyPackEntity>> packsBySubject = groupQualifyingPacksBySubject(userId);
        boolean studyGoalIsSlug = ExamGoalConfig.isValidSlug(normalizedGoal);
        String goalType = studyGoalIsSlug ? GOAL_TYPE_EXAM : GOAL_TYPE_SUBJECT;
        String goalName = studyGoalIsSlug ? ExamGoalConfig.getShortName(normalizedGoal) : normalizedGoal;
        String goalLabel = studyGoalIsSlug ? ExamGoalConfig.getFullName(normalizedGoal) : goalName;
        List<StudyPackEntity> qualifyingPacks = packsBySubject.values().stream()
                .flatMap(List::stream)
                .toList();
        List<StudyPackEntity> goalPacks = filterGoalStudyPacks(userId, normalizedGoal, studyGoalIsSlug, qualifyingPacks);
        ConceptCounts counts = countConceptProgress(goalPacks, userId, now);
        String weakestGoalSubject = resolveWeakestGoalSubject(goalPacks, userId, now);

        return new GoalNudgeResponse(
                normalizedGoal,
                goalType,
                goalName,
                goalLabel,
                masteryPercentage(counts.masteredConcepts(), counts.totalConcepts()),
                counts.dueConcepts(),
                weakestGoalSubject
        );
    }

    public List<SubjectProgressEntry> buildSubjectProgressEntries(
            List<StudyPackEntity> studyPacks,
            UUID userId,
            OffsetDateTime now
    ) {
        return groupQualifyingPacksBySubject(studyPacks).entrySet().stream()
                .map(entry -> toSubjectProgress(entry.getKey(), entry.getValue(), userId, now))
                .filter(Objects::nonNull)
                .sorted(subjectProgressComparator())
                .toList();
    }

    private Map<String, List<StudyPackEntity>> groupQualifyingPacksBySubject(UUID userId) {
        return groupQualifyingPacksBySubject(studyPackRepository.findByOwnerUserId(userId));
    }

    private Map<String, List<StudyPackEntity>> groupQualifyingPacksBySubject(List<StudyPackEntity> studyPacks) {
        Map<UUID, String> noteSubjects = fetchNoteSubjects(studyPacks);
        Map<String, List<StudyPackEntity>> packsBySubject = new LinkedHashMap<>();
        for (StudyPackEntity studyPack : studyPacks) {
            if (!hasKeyConcepts(studyPack)) {
                continue;
            }
            packsBySubject
                .computeIfAbsent(resolveSubject(studyPack, noteSubjects), ignored -> new ArrayList<>())
                .add(studyPack);
        }
        return packsBySubject;
    }

    private SubjectProgressEntry toSubjectProgress(
        String subject,
        List<StudyPackEntity> studyPacks,
        UUID userId,
        OffsetDateTime now
    ) {
        Map<String, String> conceptNamesByKey = collectConceptNamesByKey(studyPacks);
        int totalConcepts = conceptNamesByKey.size();
        if (totalConcepts == 0) {
            return null;
        }

        Map<String, List<OffsetDateTime>> reviewTimesByConceptKey = collectReviewTimesByConceptKey(studyPacks, userId);
        int masteredConcepts = 0;
        int dueConcepts = 0;
        int notPracticedConcepts = 0;

        for (String conceptKey : conceptNamesByKey.keySet()) {
            ConceptProgressState state = resolveConceptState(reviewTimesByConceptKey.get(conceptKey), now);
            if (state == ConceptProgressState.MASTERED) {
                masteredConcepts++;
            } else if (state == ConceptProgressState.DUE) {
                dueConcepts++;
            } else {
                notPracticedConcepts++;
            }
        }

        return new SubjectProgressEntry(
            subject,
            totalConcepts,
            masteredConcepts,
            dueConcepts,
            notPracticedConcepts,
            masteryPercentage(masteredConcepts, totalConcepts)
        );
    }

    private GoalSummaryResponse buildGoalSummary(
            UUID userId,
            String studyGoal,
            List<String> focusSubjects,
            Map<String, List<StudyPackEntity>> packsBySubject,
            OffsetDateTime now
    ) {
        if (studyGoal == null && focusSubjects.isEmpty()) {
            return null;
        }

        if (studyGoal == null) {
            return buildSubjectFocusGoalSummary(userId, focusSubjects, packsBySubject, now);
        }

        boolean studyGoalIsSlug = ExamGoalConfig.isValidSlug(studyGoal);
        String goalType = studyGoalIsSlug ? GOAL_TYPE_EXAM : GOAL_TYPE_SUBJECT;
        String goalName = studyGoalIsSlug ? ExamGoalConfig.getShortName(studyGoal) : studyGoal;
        String goalLabel = studyGoalIsSlug ? ExamGoalConfig.getFullName(studyGoal) : goalName;
        List<StudyPackEntity> qualifyingPacks = packsBySubject.values().stream()
                .flatMap(List::stream)
                .toList();
        List<StudyPackEntity> goalPacks = filterGoalStudyPacks(userId, studyGoal, studyGoalIsSlug, qualifyingPacks);
        ConceptCounts counts = countConceptProgress(goalPacks, userId, now);
        String weakestGoalSubject = resolveWeakestGoalSubject(goalPacks, userId, now);

        return new GoalSummaryResponse(
                studyGoal,
                goalType,
                goalName,
                goalLabel,
                masteryPercentage(counts.masteredConcepts(), counts.totalConcepts()),
                counts.masteredConcepts(),
                counts.totalConcepts(),
                counts.notPracticedConcepts(),
                weakestGoalSubject
        );
    }

    private GoalSummaryResponse buildSubjectFocusGoalSummary(
            UUID userId,
            List<String> focusSubjects,
            Map<String, List<StudyPackEntity>> packsBySubject,
            OffsetDateTime now
    ) {
        Set<String> focusSubjectKeys = focusSubjects.stream()
                .map(SubjectNormalizationUtils::normalizeForLookup)
                .filter(key -> !key.isBlank())
                .collect(Collectors.toSet());
        List<StudyPackEntity> goalPacks = packsBySubject.entrySet().stream()
                .filter(entry -> focusSubjectKeys.contains(SubjectNormalizationUtils.normalizeForLookup(entry.getKey())))
                .flatMap(entry -> entry.getValue().stream())
                .toList();
        ConceptCounts counts = countConceptProgress(goalPacks, userId, now);
        if (counts.totalConcepts() == 0) {
            return null;
        }

        String goalName = focusSubjects.size() == 1
                ? focusSubjects.getFirst()
                : focusSubjects.size() + " subjects in focus";
        String studyGoalDisplay = String.join(", ", focusSubjects);

        return new GoalSummaryResponse(
                studyGoalDisplay,
                GOAL_TYPE_SUBJECT_FOCUS,
                goalName,
                goalName,
                masteryPercentage(counts.masteredConcepts(), counts.totalConcepts()),
                counts.masteredConcepts(),
                counts.totalConcepts(),
                counts.notPracticedConcepts(),
                resolveWeakestGoalSubject(goalPacks, userId, now)
        );
    }

    private List<StudyPackEntity> filterGoalStudyPacks(
            UUID userId,
            String studyGoal,
            boolean studyGoalIsSlug,
            List<StudyPackEntity> studyPacks
    ) {
        if (studyPacks.isEmpty()) {
            return List.of();
        }

        List<UUID> noteIds = studyPacks.stream()
                .map(StudyPackEntity::getNoteId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (noteIds.isEmpty()) {
            return List.of();
        }

        List<String> goalCourseProgramValues = studyGoalIsSlug ? ExamGoalConfig.getCoursePrograms(studyGoal) : List.of(studyGoal);
        Set<String> goalCoursePrograms = goalCourseProgramValues.stream()
                .map(this::normalizeCourseProgramKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> matchingNoteIds = noteRepository.findByOwnerUserIdAndIdIn(userId, noteIds).stream()
                .filter(note -> goalCoursePrograms.contains(normalizeCourseProgramKey(note.getCourseProgram())))
                .map(NoteEntity::getId)
                .collect(Collectors.toSet());

        return studyPacks.stream()
                .filter(studyPack -> matchingNoteIds.contains(studyPack.getNoteId()))
                .toList();
    }

    private ConceptCounts countConceptProgress(List<StudyPackEntity> studyPacks, UUID userId, OffsetDateTime now) {
        Map<String, String> conceptNamesByKey = collectConceptNamesByKey(studyPacks);
        int totalConcepts = conceptNamesByKey.size();
        if (totalConcepts == 0) {
            return new ConceptCounts(0, 0, 0, 0);
        }

        Map<String, List<OffsetDateTime>> reviewTimesByConceptKey = collectReviewTimesByConceptKey(studyPacks, userId);
        int masteredConcepts = 0;
        int dueConcepts = 0;
        int notPracticedConcepts = 0;
        for (String conceptKey : conceptNamesByKey.keySet()) {
            ConceptProgressState state = resolveConceptState(reviewTimesByConceptKey.get(conceptKey), now);
            if (state == ConceptProgressState.MASTERED) {
                masteredConcepts++;
            } else if (state == ConceptProgressState.DUE) {
                dueConcepts++;
            } else {
                notPracticedConcepts++;
            }
        }
        return new ConceptCounts(totalConcepts, masteredConcepts, dueConcepts, notPracticedConcepts);
    }

    private String resolveWeakestGoalSubject(List<StudyPackEntity> goalPacks, UUID userId, OffsetDateTime now) {
        if (goalPacks.isEmpty()) {
            return null;
        }

        Map<UUID, String> noteSubjects = fetchNoteSubjects(goalPacks);
        return goalPacks.stream()
                .collect(Collectors.groupingBy(sp -> resolveSubject(sp, noteSubjects), LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> toSubjectProgress(entry.getKey(), entry.getValue(), userId, now))
                .filter(Objects::nonNull)
                .max(Comparator
                        .comparingInt((SubjectProgressEntry entry) -> entry.notPracticedConcepts() + entry.dueConcepts())
                        .thenComparing(SubjectProgressEntry::subject, String.CASE_INSENSITIVE_ORDER.reversed()))
                .map(SubjectProgressEntry::subject)
                .orElse(null);
    }

    private Map<String, String> collectConceptNamesByKey(List<StudyPackEntity> studyPacks) {
        Map<String, String> conceptNamesByKey = new LinkedHashMap<>();
        for (StudyPackEntity studyPack : studyPacks) {
            for (String rawConcept : studyPack.getKeyConcepts()) {
                String concept = normalizeConcept(rawConcept);
                if (concept == null) {
                    continue;
                }
                conceptNamesByKey.putIfAbsent(normalizeConceptKey(concept), concept);
            }
        }
        return conceptNamesByKey;
    }

    private Map<String, List<OffsetDateTime>> collectReviewTimesByConceptKey(
        List<StudyPackEntity> studyPacks,
        UUID userId
    ) {
        Map<String, List<OffsetDateTime>> reviewTimesByConceptKey = new HashMap<>();
        for (StudyPackEntity studyPack : studyPacks) {
            for (ConceptHealthEntity health : conceptHealthRepository.findByUserIdAndStudyPackId(userId, studyPack.getId())) {
                String concept = normalizeConcept(health.getConcept());
                if (concept == null) {
                    continue;
                }
                reviewTimesByConceptKey
                    .computeIfAbsent(normalizeConceptKey(concept), ignored -> new ArrayList<>())
                    .add(health.getLastCorrectAt());
            }
        }
        return reviewTimesByConceptKey;
    }

    private ConceptProgressState resolveConceptState(List<OffsetDateTime> reviewTimes, OffsetDateTime now) {
        if (reviewTimes == null || reviewTimes.isEmpty()) {
            return ConceptProgressState.NOT_PRACTICED;
        }

        boolean hasDueReview = false;
        for (OffsetDateTime lastCorrectAt : reviewTimes) {
            if (lastCorrectAt == null) {
                continue;
            }
            if (!conceptHealthService.isDue(lastCorrectAt, now)) {
                return ConceptProgressState.MASTERED;
            }
            hasDueReview = true;
        }
        return hasDueReview ? ConceptProgressState.DUE : ConceptProgressState.NOT_PRACTICED;
    }

    private Comparator<SubjectProgressEntry> subjectProgressComparator() {
        return Comparator
            .comparing((SubjectProgressEntry entry) -> OTHER_SUBJECT.equals(entry.subject()))
            .thenComparingInt(SubjectProgressEntry::masteryPercentage)
            .thenComparing(SubjectProgressEntry::subject, String.CASE_INSENSITIVE_ORDER);
    }

    private int masteryPercentage(int masteredConcepts, int totalConcepts) {
        if (totalConcepts == 0) {
            return 0;
        }
        return (int) Math.round(masteredConcepts * 100.0 / totalConcepts);
    }

    private Map<UUID, String> fetchNoteSubjects(List<StudyPackEntity> studyPacks) {
        List<UUID> noteIds = studyPacks.stream()
                .map(StudyPackEntity::getNoteId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (noteIds.isEmpty()) {
            return Map.of();
        }
        return noteRepository.findAllById(noteIds).stream()
                .filter(n -> n.getSubject() != null && !n.getSubject().isBlank())
                .collect(Collectors.toMap(NoteEntity::getId, NoteEntity::getSubject));
    }

    private String resolveSubject(StudyPackEntity studyPack, Map<UUID, String> noteSubjects) {
        UUID noteId = studyPack.getNoteId();
        if (noteId != null) {
            String noteSubject = noteSubjects.get(noteId);
            if (noteSubject != null && !noteSubject.isBlank()) {
                return noteSubject.trim();
            }
        }
        String subject = studyPack.getSubject();
        if (subject == null || subject.isBlank()) {
            return OTHER_SUBJECT;
        }
        return subject.trim();
    }

    private boolean hasKeyConcepts(StudyPackEntity studyPack) {
        return studyPack.getKeyConcepts() != null && !studyPack.getKeyConcepts().isEmpty();
    }

    private String normalizeConcept(String rawConcept) {
        if (rawConcept == null) {
            return null;
        }
        String concept = rawConcept.trim();
        return concept.isBlank() ? null : concept;
    }

    private String normalizeConceptKey(String concept) {
        return concept.toLowerCase(Locale.ROOT);
    }

    private String normalizeCourseProgramKey(String courseProgram) {
        if (courseProgram == null || courseProgram.isBlank()) {
            return null;
        }
        return courseProgram.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> normalizeFocusSubjects(List<String> focusSubjects) {
        if (focusSubjects == null || focusSubjects.isEmpty()) {
            return List.of();
        }
        Map<String, String> subjectsByLookupKey = new LinkedHashMap<>();
        for (String focusSubject : focusSubjects) {
            String normalizedSubject = SubjectNormalizationUtils.normalizeForStorage(focusSubject);
            if (normalizedSubject == null) {
                continue;
            }
            subjectsByLookupKey.putIfAbsent(
                    SubjectNormalizationUtils.normalizeForLookup(normalizedSubject),
                    normalizedSubject
            );
        }
        return List.copyOf(subjectsByLookupKey.values());
    }

    private String normalizeGoal(String studyGoal) {
        if (studyGoal == null || studyGoal.isBlank()) {
            return null;
        }
        String normalizedGoal = studyGoal.trim();
        return ExamGoalConfig.isValidSlug(normalizedGoal) ? normalizedGoal.toLowerCase(Locale.ROOT) : normalizedGoal;
    }

    private record ConceptCounts(
            int totalConcepts,
            int masteredConcepts,
            int dueConcepts,
            int notPracticedConcepts
    ) {
    }

    private enum ConceptProgressState {
        MASTERED,
        DUE,
        NOT_PRACTICED
    }
}
