package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record NoteListItemResponse(
        String id,
        String title,
        String subject,
        List<String> tags,
        String contentPreview,
        String studyPackId,
        String studyPackStatus,
        OffsetDateTime updatedAt
) {
}
