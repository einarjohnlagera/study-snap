package com.studysnap.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ChallengeQuizPerformanceSummaryResponse(
        BigDecimal bestScorePercentage,
        Long attempts,
        BigDecimal lastScorePercentage,
        OffsetDateTime lastCompletedAt,
        String latestPerformanceLevel,
        List<String> latestWeakConcepts
) {
}
