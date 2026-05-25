package com.studysnap.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AdminIssueRefundResponse(
        UUID transactionId,
        String userEmail,
        BigDecimal amount,
        String currency
) {
}
