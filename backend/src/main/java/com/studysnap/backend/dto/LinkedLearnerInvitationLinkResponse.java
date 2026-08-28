package com.studysnap.backend.dto;

import com.studysnap.backend.entity.LinkedLearnerSide;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LinkedLearnerInvitationLinkResponse(
        UUID id,
        String token,
        String url,
        LinkedLearnerSide creatorRole,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {
}

