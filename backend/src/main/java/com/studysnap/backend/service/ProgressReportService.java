package com.studysnap.backend.service;

import com.studysnap.backend.dto.ProgressReportResponse;
import com.studysnap.backend.dto.SubjectProgressEntry;
import com.studysnap.backend.entity.ConceptHealthEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.ConceptHealthRepository;
import com.studysnap.backend.repository.StudyPackRepository;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProgressReportService {
    private static final String OTHER_SUBJECT = "Other";

    private final StudyPackRepository studyPackRepository;
    private final ConceptHealthRepository conceptHealthRepository;
    private final ConceptHealthService conceptHealthService;

    @Transactional(readOnly = true)
    public ProgressReportResponse getProgressReport(UUID userId, OffsetDateTime now) {
        Map<String, List<StudyPackEntity>> packsBySubject = groupQualifyingPacksBySubject(userId);
        List<SubjectProgressEntry> subjects = packsBySubject.entrySet().stream()
            .map(entry -> toSubjectProgress(entry.getKey(), entry.getValue(), userId, now))
            .filter(Objects::nonNull)
            .sorted(subjectProgressComparator())
            .toList();
        return new ProgressReportResponse(subjects);
    }

    private Map<String, List<StudyPackEntity>> groupQualifyingPacksBySubject(UUID userId) {
        Map<String, List<StudyPackEntity>> packsBySubject = new LinkedHashMap<>();
        for (StudyPackEntity studyPack : studyPackRepository.findByOwnerUserId(userId)) {
            if (!hasKeyConcepts(studyPack)) {
                continue;
            }
            packsBySubject
                .computeIfAbsent(resolveSubject(studyPack), ignored -> new ArrayList<>())
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

    private String resolveSubject(StudyPackEntity studyPack) {
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

    private enum ConceptProgressState {
        MASTERED,
        DUE,
        NOT_PRACTICED
    }
}
