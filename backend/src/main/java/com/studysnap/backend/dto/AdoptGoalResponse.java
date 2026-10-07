package com.studysnap.backend.dto;

import java.util.UUID;

public record AdoptGoalResponse(
        UUID goalCollectionId,
        int adoptedSubjectCount,
        int skippedSubjectCount,
        int totalNotesCopied,
        int totalNotesSkipped,
        boolean alreadyAdopted,
        UUID collectionId,
        String status,
        UUID jobId,
        int processedSubjectCount,
        int totalSubjectCount
) {
    public AdoptGoalResponse(UUID goalCollectionId, int adoptedSubjectCount, int skippedSubjectCount,
                             int totalNotesCopied, int totalNotesSkipped, boolean alreadyAdopted) {
        this(goalCollectionId, adoptedSubjectCount, skippedSubjectCount, totalNotesCopied,
                totalNotesSkipped, alreadyAdopted, goalCollectionId, "COMPLETED", null,
                adoptedSubjectCount + skippedSubjectCount, adoptedSubjectCount + skippedSubjectCount);
    }
}
