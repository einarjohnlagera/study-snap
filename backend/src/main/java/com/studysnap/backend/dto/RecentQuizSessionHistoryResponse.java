package com.studysnap.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record RecentQuizSessionHistoryResponse(
        String sessionId,
        String sessionMode,
        int totalQuestions,
        int correctAnswers,
        BigDecimal scorePercentage,
        int retryCount,
        String performanceLevel,
        List<String> weakConcepts,
        int participatingNoteCount,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}
