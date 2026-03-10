package com.studysnap.backend.dto;

import com.studysnap.backend.entity.QuickReviewRound;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ContinueStudyingResponse(
        String studyPackId,
        String title,
        String summaryPreview,
        ContinueStudyingReason reason,
        BigDecimal lastScorePercentage,
        OffsetDateTime lastReviewedAt,
        OffsetDateTime lastOpenedAt,
        OffsetDateTime createdAt,
        Integer currentQuestionIndex,
        Integer totalQuestions,
        QuickReviewRound currentRound,
        Integer remainingQuestions
) {
}
