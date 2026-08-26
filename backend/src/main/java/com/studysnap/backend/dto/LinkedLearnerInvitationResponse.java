package com.studysnap.backend.dto;

import com.studysnap.backend.entity.LinkedLearnerSide;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A pending invitation, which is deliberately NOT a connection.
 *
 * <p>⚠️ Carries no counterparty display name in either direction. For an outgoing invitation the
 * inviter already knows the address they typed and must learn nothing more, or the list becomes a
 * name-harvesting oracle again. For an incoming one the inviter's name is disclosed, because the
 * recipient needs to know who is asking in order to decide.
 */
public record LinkedLearnerInvitationResponse(
        UUID id,
        boolean incoming,
        LinkedLearnerSide inviterRole,
        String invitedEmail,
        String inviterName,
        OffsetDateTime createdAt
) {
}
