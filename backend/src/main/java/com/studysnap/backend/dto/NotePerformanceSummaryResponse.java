package com.studysnap.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record NotePerformanceSummaryResponse(
        UUID noteId,
        String noteTitle,
        BigDecimal bestScore,
        BigDecimal averageScore,
        int attemptCount,
        OffsetDateTime lastAttemptedAt,
        UUID bestSessionId,
        String bestSessionMode
) {}
