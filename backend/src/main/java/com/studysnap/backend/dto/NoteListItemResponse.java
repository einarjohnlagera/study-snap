package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record NoteListItemResponse(
        String id,
        String ownerUserId,
        String title,
        String courseProgram,
        String targetProfileType,
        String subject,
        List<String> tags,
        String contentPreview,
        String summaryPreview,
        String visibility,
        String studyPackId,
        String studyPackStatus,
        Integer quizCount,
        Long copyCount,
        Long likeCount,
        Long shareCount,
        Long viewCount,
        String authorDisplayName,
        String authorUsername,
        boolean isOfficialAuthor,
        boolean isCurrentUser,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String generatedQuizId,
        OffsetDateTime generatedQuizGeneratedAt,
        Integer generatedQuizQuestionCount,
        String copiedFromNoteId,
        boolean copiedFromPublic,
        boolean likedByCurrentUser
) {
}
