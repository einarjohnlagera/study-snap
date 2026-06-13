package com.studysnap.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NoteCollectionDetailResponse(
        UUID id,
        String title,
        String description,
        Instant createdAt,
        Instant updatedAt,
        List<NoteCollectionItemResponse> items
) {
}
