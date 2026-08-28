package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class LinkedLearnerInvitationLinkNotFoundException extends AppException {
    public LinkedLearnerInvitationLinkNotFoundException() {
        super(
                "LINKED_LEARNER_INVITATION_LINK_NOT_FOUND",
                "This invitation link is not available.",
                HttpStatus.NOT_FOUND
        );
    }
}

