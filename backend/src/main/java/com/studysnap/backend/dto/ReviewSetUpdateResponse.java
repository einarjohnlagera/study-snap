package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record ReviewSetUpdateResponse(
        UUID collectionId,
        String sourceState,
        String status,
        int additionsAvailable,
        int notesAdded,
        int subjectPlansAdded,
        int skippedCount,
        List<ReviewSetUpdateChange> changes
) {
}
