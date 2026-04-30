package com.studysnap.backend.dto;

import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.PlanType;

public record BillingCheckoutSessionRequest(
        PlanType planType,
        BillingCycle billingCycle,
        String returnUrl
) {
}
