package com.studysnap.backend.dto;

import java.math.BigDecimal;

public record BillingPricingResponse(
        String region,
        String currency,
        BigDecimal monthlyPrice,
        BigDecimal yearlyPrice,
        BigDecimal introMonthlyPrice,
        boolean hasIntroPromo,
        boolean introEligible
) {
}
