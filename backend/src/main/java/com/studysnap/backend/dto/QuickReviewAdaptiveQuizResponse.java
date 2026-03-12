package com.studysnap.backend.dto;

import java.util.List;

public record QuickReviewAdaptiveQuizResponse(
        String studyPackId,
        String title,
        List<String> weakConcepts,
        List<QuizItem> quiz,
        String message
) {
}
