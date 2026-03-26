package com.studysnap.backend.dto;

import java.math.BigDecimal;

public record DashboardPerformanceSummaryResponse(
        BigDecimal averageQuizScore,
        int totalQuizzesTaken,
        long studyPacksCreated,
        DashboardConceptInsightResponse strongestConcept,
        DashboardConceptInsightResponse weakestConcept
) {
}
