package com.studysnap.backend.dto;

import com.studysnap.backend.entity.QuickReviewSessionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ChallengeQuizSessionResponse(
        String sessionId,
        String studyPackId,
        QuickReviewSessionStatus status,
        int totalQuestions,
        int correctAnswers,
        BigDecimal scorePercentage,
        Integer durationSeconds,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}
