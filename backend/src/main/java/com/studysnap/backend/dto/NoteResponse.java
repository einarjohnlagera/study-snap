package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record NoteResponse(
        String id,
        String title,
        String subject,
        List<String> tags,
        String content,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
