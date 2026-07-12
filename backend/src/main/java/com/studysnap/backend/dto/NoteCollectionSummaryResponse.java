package com.studysnap.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record NoteCollectionSummaryResponse(
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
}
