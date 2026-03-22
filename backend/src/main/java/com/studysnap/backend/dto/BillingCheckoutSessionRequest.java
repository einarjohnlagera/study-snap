package com.studysnap.backend.dto;

import com.studysnap.backend.entity.BillingCycle;

public record BillingCheckoutSessionRequest(
        BillingCycle billingCycle
) {
}
