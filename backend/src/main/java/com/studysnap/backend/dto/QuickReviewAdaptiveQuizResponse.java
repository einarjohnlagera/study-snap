package com.studysnap.backend.dto;

import com.studysnap.backend.entity.QuickReviewSessionStatus;

import java.util.List;

public record QuickReviewAdaptiveQuizResponse(
        String sessionId,
        QuickReviewSessionStatus status,
        String studyPackId,
        String title,
        List<String> weakConcepts,
        List<QuizItem> quiz,
        String message
) {
}
