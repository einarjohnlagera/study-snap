package com.studysnap.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record QuickReviewSessionResponse(
        String id,
        String studyPackId,
        int totalQuestions,
        int correctAnswers,
        BigDecimal scorePercentage,
        int retryCount,
        Integer durationSeconds,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}
