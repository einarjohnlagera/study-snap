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
        /**
         * When an unconfirmed request lapses, or null when it cannot lapse.
         *
         * <p>⚠️ Null carries MEANING and is not "unknown": acceptance clears the deadline, and a
         * consent pause leaves it clear, so a null here means this relationship is not on the
         * expiry clock at all. Surfaces must render the absence as "no deadline", never as a
         * missing value.
         */
        OffsetDateTime expiresAt,
        boolean birthYearRequired,
        boolean guardianConsentRequired,
        boolean guardianConsentRecorded,
        boolean activitySharedByMe,
        boolean activitySharedWithMe,
        boolean progressSharedByMe,
        boolean progressSharedWithMe
) {
}
