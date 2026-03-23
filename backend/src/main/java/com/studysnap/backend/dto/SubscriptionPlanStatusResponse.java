package com.studysnap.backend.dto;

import java.time.OffsetDateTime;

public record SubscriptionPlanStatusResponse(
        boolean cancelAtPeriodEnd,
        OffsetDateTime premiumEndsAt,
        OffsetDateTime cancelledAt
) {
}
