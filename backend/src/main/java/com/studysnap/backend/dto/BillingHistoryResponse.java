package com.studysnap.backend.dto;

import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record BillingHistoryResponse(
        PlanType currentPlan,
        SubscriptionStatus subscriptionStatus,
        BillingCycle billingType,
        OffsetDateTime currentPeriodStart,
        OffsetDateTime currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        OffsetDateTime cancellationEffectiveAt,
        List<BillingHistoryItemResponse> transactions
) {
}
