package com.studysnap.backend.dto;

import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewConfidenceLevel;
import com.studysnap.backend.entity.QuickReviewSessionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record QuickReviewSessionResponse(
        String id,
        String studyPackId,
        QuickReviewSessionStatus status,
        int currentQuestionIndex,
        QuickReviewRound currentRound,
        int totalQuestions,
        int correctAnswers,
        BigDecimal scorePercentage,
        int retryCount,
        Integer durationSeconds,
        QuickReviewConfidenceLevel confidenceLevel,
        List<String> weakConcepts,
        Map<String, Object> sessionState,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        boolean isFirstCompletedQuiz,
        boolean isFirstCompletedSessionEver,
        boolean isSecondCompletedSessionEver,
        List<String> twiceMissedConcepts
) {
}
