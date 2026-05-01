package com.studysnap.backend.dto;

import com.studysnap.backend.entity.PlanType;

import java.math.BigDecimal;

public record BillingPricingResponse(
        String region,
        String currency,
        PlanPricing plus,
        PlanPricing pro
) {
    public record PlanPricing(
            PlanType planType,
            CyclePricing monthly,
            CyclePricing yearly
    ) {
    }

    public record CyclePricing(
            BigDecimal amount,
            Integer durationDays,
            BigDecimal introAmount,
            boolean introEligible,
            boolean available
    ) {
    }
}
