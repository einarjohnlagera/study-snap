package com.studysnap.backend.repository;

import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record QuickReviewSessionMetadataProjection(
        UUID id,
        UUID userId,
        UUID studyPackId,
        UUID noteId,
        QuickReviewSessionMode sessionMode,
        QuickReviewSessionStatus status,
        Integer totalQuestions,
        Integer correctAnswers,
        BigDecimal scorePercentage,
        Integer retryCount,
        Integer durationSeconds,
        Map<String, Object> sessionMetadata,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}
