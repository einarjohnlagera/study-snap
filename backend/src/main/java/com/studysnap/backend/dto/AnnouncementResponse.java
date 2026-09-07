package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AnnouncementResponse(
        UUID id,
        String title,
        String body,
        String ctaLabel,
        String ctaPath,
        String audience,
        String audienceValue,
        String status,
        OffsetDateTime publishedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        boolean editable,
        boolean expired
) {
}
