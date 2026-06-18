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
        int itemCount,
        Instant createdAt,
        Instant updatedAt
) {
}
