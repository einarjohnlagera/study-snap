package com.studysnap.backend.dto;

import com.studysnap.backend.entity.QuickReviewSessionStatus;

import java.util.List;

public record QuickReviewAdaptiveQuizResponse(
        String sessionId,
        QuickReviewSessionStatus status,
        String studyPackId,
        String noteId,
        String title,
        List<AdaptivePracticeFocusConceptResponse> focusConcepts,
        List<QuizItem> quiz,
        String message
) {
}
