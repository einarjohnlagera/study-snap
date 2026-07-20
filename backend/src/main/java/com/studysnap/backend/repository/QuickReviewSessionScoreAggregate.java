package com.studysnap.backend.repository;

import java.math.BigDecimal;

public record QuickReviewSessionScoreAggregate(
        long count,
        BigDecimal totalScore
) {
}
