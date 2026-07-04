package com.studysnap.backend.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StudyPackListItemProjection(
        UUID id,
        String title,
        String summary,
        String subject,
        String[] tags,
        OffsetDateTime createdAt
) {
}
