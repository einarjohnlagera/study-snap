package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record PlanReadinessResponse(
        UUID collectionId,
        int totalNotes,
        int notesWithStudyPack,
        int overallReadinessPercentage,
        int totalConcepts,
        int masteredConcepts,
        int dueConcepts,
        int notPracticedConcepts,
        List<SubjectProgressEntry> subjects
) {
}
