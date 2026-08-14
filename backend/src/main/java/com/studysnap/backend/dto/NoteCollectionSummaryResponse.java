package com.studysnap.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record NoteCollectionSummaryResponse(
        UUID id,
        String title,
        String description,
        String visibility,
        String courseProgram,
        String learnerLevel,
        String resolvedLearnerLevel,
        UUID sourcePlanId,
        UUID parentCollectionId,
        int itemCount,
        int readyCount,
        int childCount,
        int notesPracticed,
        Instant createdAt,
        Instant updatedAt
) {
    public NoteCollectionSummaryResponse(
            UUID id,
            String title,
            String description,
            String visibility,
            String courseProgram,
            UUID sourcePlanId,
            UUID parentCollectionId,
            int itemCount,
            int readyCount,
            int childCount,
            int notesPracticed,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, title, description, visibility, courseProgram, null, null, sourcePlanId,
                parentCollectionId, itemCount, readyCount, childCount, notesPracticed, createdAt, updatedAt);
    }
}
