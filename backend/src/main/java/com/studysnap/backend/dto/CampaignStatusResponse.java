package com.studysnap.backend.dto;

import java.time.OffsetDateTime;

public record CampaignStatusResponse(
        int eligibleCount,
        long totalSent,
        OffsetDateTime lastSentAt
) {}
