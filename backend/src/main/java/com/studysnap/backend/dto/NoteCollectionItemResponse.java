package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record NoteCollectionItemResponse(
        UUID noteId,
        String label,
        int position,
        String title,
        String subject,
        String courseProgram,
        String domainContext,
        String learnerLevel,
        String studyPackStatus,
        String studyPackId,
        String generatedQuizId,
        OffsetDateTime lastSessionCompletedAt,
        int dueConceptCount,
        List<String> dueConcepts,
        OffsetDateTime updatedAt
) {
}
