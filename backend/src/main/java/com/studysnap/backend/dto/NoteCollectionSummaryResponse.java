package com.studysnap.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record NoteCollectionSummaryResponse(
        UUID id,
        String title,
        String description,
        int itemCount,
        Instant createdAt,
        Instant updatedAt
) {
}
