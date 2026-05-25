package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdminIssueRefundRequest(
        @NotNull UUID transactionId
) {
}
