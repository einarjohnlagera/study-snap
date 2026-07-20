package com.studysnap.backend.dto;

import com.studysnap.backend.entity.QuickReviewSessionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ChallengeQuizSessionResponse(
        String sessionId,
        String studyPackId,
        QuickReviewSessionStatus status,
        int totalQuestions,
        int correctAnswers,
        BigDecimal scorePercentage,
        String performanceLevel,
        List<ChallengeQuizConceptStatResponse> conceptBreakdown,
        List<String> weakConcepts,
        Integer durationSeconds,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        boolean isFirstCompletedSessionEver
) {
}
