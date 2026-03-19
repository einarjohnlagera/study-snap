package com.studysnap.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ChallengeQuizSessionSummaryResponse(
        String sessionId,
        int totalQuestions,
        int correctAnswers,
        BigDecimal scorePercentage,
        String performanceLevel,
        List<String> weakConcepts,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}
