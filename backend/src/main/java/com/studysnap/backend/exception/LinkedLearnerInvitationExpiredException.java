package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * An invitation is a standing offer to whoever controls an ADDRESS, so it lapses. Distinct from
 * {@link LinkedLearnerInvalidStateException} because the recovery differs: an expired invitation is
 * re-armed by the inviter simply sending it again, while a revoked or already-accepted one is not.
 */
public class LinkedLearnerInvitationExpiredException extends AppException {
    public LinkedLearnerInvitationExpiredException() {
        super(
                "LINKED_LEARNER_INVITATION_EXPIRED",
                "This invitation has expired. Ask for a new one.",
                HttpStatus.GONE
        );
    }
}
