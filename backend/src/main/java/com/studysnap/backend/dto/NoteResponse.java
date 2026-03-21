package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record NoteResponse(
        String id,
        String title,
        String subject,
        List<String> tags,
        String content,
        String visibility,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String studyPackId,
        String studyPackStatus,
        String summary,
        List<String> keyConcepts,
        List<QuizItem> quiz,
        Integer quizCount,
        boolean quickReviewAvailable,
        boolean challengeQuizAvailable,
        boolean adaptivePracticeAvailable
) {
}
