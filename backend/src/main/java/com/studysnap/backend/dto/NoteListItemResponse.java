package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record NoteListItemResponse(
        String id,
        String ownerUserId,
        String title,
        String subject,
        List<String> tags,
        String contentPreview,
        String summaryPreview,
        String visibility,
        String studyPackId,
        String studyPackStatus,
        Integer quizCount,
        String authorDisplayName,
        boolean isOfficialAuthor,
        boolean isCurrentUser,
        OffsetDateTime updatedAt
) {
}
