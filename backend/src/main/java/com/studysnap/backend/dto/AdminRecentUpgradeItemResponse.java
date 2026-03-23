package com.studysnap.backend.dto;

import com.studysnap.backend.entity.BillingProvider;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminRecentUpgradeItemResponse(
        UUID subscriptionId,
        String userEmail,
        String billingCycle,
        BillingProvider provider,
        boolean cancelAtPeriodEnd,
        OffsetDateTime startedAt
) {
}
