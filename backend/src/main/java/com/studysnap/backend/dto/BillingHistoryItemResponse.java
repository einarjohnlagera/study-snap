package com.studysnap.backend.dto;

import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.PaymentTransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BillingHistoryItemResponse(
        java.util.UUID id,
        OffsetDateTime date,
        String description,
        BigDecimal amount,
        String currency,
        PaymentTransactionStatus status,
        BillingProvider provider,
        String providerReferenceId
) {
}
