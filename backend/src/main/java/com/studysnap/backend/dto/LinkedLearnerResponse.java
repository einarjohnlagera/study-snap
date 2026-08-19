package com.studysnap.backend.dto;

import com.studysnap.backend.entity.LinkedLearnerSide;
import com.studysnap.backend.entity.LinkedLearnerStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LinkedLearnerResponse(
        UUID id,
        LinkedLearnerSide callerRole,
        LinkedLearnerSide initiatedBy,
        boolean incomingInvitation,
        String counterpartyDisplayName,
        String counterpartyEmail,
        LinkedLearnerStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime acceptedAt,
        OffsetDateTime revokedAt,
        boolean birthYearRequired,
        boolean guardianConsentRequired,
        boolean guardianConsentRecorded
) {
}
