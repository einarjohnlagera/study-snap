package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PublicNoteDetailResponse(
        String id,
        String ownerUserId,
        String title,
        String subject,
        List<String> tags,
        String content,
        String contentPreview,
        String studyPackStatus,
        String summary,
        List<String> keyConcepts,
        List<QuizItem> quiz,
        String authorDisplayName,
        boolean isOfficialAuthor,
        boolean isCurrentUser,
        OffsetDateTime updatedAt
) {
}
