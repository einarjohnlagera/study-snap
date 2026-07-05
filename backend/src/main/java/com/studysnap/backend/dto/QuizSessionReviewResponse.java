package com.studysnap.backend.dto;

import com.studysnap.backend.entity.QuickReviewSessionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record QuizSessionReviewResponse(
        String sessionId,
        String studyPackId,
        String sessionMode,
        QuickReviewSessionStatus status,
        int totalQuestions,
        int correctAnswers,
        BigDecimal scorePercentage,
        int retryCount,
        Integer durationSeconds,
        List<String> weakConcepts,
        List<ChallengeQuizConceptStatResponse> conceptBreakdown,
        List<QuizItem> quiz,
        Map<Integer, Integer> selectedChoices,
        Map<Integer, List<Integer>> selectedMultiChoices,
        Map<Integer, String> selectedIdentificationAnswers,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}
