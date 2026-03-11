package com.studysnap.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record QuickReviewPerformanceSummaryResponse(
        BigDecimal bestScorePercentage,
        Long attempts,
        BigDecimal lastScorePercentage,
        OffsetDateTime lastReviewedAt
) {
}
