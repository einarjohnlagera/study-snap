package com.studysnap.backend.dto;

import java.math.BigDecimal;

public record DashboardConceptInsightResponse(
        String conceptName,
        BigDecimal accuracyPercentage
) {
}
