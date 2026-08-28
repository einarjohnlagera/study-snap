package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class LinkedLearnerInvitationLinkTokenGenerationException extends AppException {
    public LinkedLearnerInvitationLinkTokenGenerationException() {
        super(
                "LINKED_LEARNER_INVITATION_LINK_TOKEN_GENERATION_FAILED",
                "Could not create an invitation link. Please try again.",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
