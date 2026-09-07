package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String body,
        String ctaLabel,
        String ctaPath,
        OffsetDateTime createdAt,
        OffsetDateTime readAt,
        OffsetDateTime dismissedAt
) {
}
