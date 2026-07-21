package com.studysnap.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AdminOrganicLandingsResponse(
        List<Landing> landings,
        long googleExamHubViews,
        long examHubCtaClicks,
        BigDecimal examHubOrganicClickThroughRatio
) {
    public record Landing(
            LocalDate weekStart,
            String eventType,
            String referrerSource,
            long count
    ) {
    }
}
