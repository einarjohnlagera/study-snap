package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NoteShareResponse(
        UUID relationshipId,
        String granteeDisplayName,
        String granteeEmail,
        OffsetDateTime createdAt
) {
}
