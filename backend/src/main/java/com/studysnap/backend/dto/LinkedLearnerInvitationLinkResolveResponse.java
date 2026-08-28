package com.studysnap.backend.dto;

import com.studysnap.backend.entity.LinkedLearnerSide;

/** Deliberately contains no email address or user id. */
public record LinkedLearnerInvitationLinkResolveResponse(
        String inviterName,
        LinkedLearnerSide inviterRole
) {
}

