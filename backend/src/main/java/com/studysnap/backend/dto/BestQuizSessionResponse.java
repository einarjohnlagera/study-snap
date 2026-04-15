package com.studysnap.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BestQuizSessionResponse(
        UUID sessionId,
        UUID noteId,
        String noteTitle,
        String sessionMode,
        int totalQuestions,
        int correctAnswers,
        BigDecimal scorePercentage,
        OffsetDateTime completedAt
) {
}
