package com.studysnap.backend.dto;

import com.studysnap.backend.entity.LinkedLearnerStatus;

import java.util.UUID;

public record LinkedLearnerInvitationLinkRedemptionResponse(
        UUID relationshipId,
        LinkedLearnerStatus status
) {
}

