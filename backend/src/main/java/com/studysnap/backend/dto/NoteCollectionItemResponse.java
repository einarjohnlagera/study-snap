package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NoteCollectionItemResponse(
        UUID noteId,
        String label,
        int position,
        String title,
        String subject,
        String courseProgram,
        String studyPackStatus,
        String generatedQuizId,
        OffsetDateTime lastSessionCompletedAt,
        OffsetDateTime updatedAt
) {
}
