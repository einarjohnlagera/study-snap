package com.studysnap.backend.dto;

import com.studysnap.backend.entity.BillingProvider;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminRecentUpgradeItemResponse(
        UUID subscriptionId,
        String userEmail,
        String billingCycle,
        BillingProvider provider,
        String transactionId,
        BigDecimal amount,
        String currency,
        boolean cancelAtPeriodEnd,
        OffsetDateTime startedAt
) {
}
