package com.studysnap.backend.dto;

import com.studysnap.backend.entity.BillingProvider;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminRecentFailedPaymentItemResponse(
        UUID transactionId,
        String userEmail,
        BigDecimal amount,
        String currency,
        BillingProvider provider,
        OffsetDateTime createdAt
) {
}
