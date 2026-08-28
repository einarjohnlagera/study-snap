package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class TooManyLinkedLearnerInvitationsException extends AppException {
    public TooManyLinkedLearnerInvitationsException() {
        super(
                "TOO_MANY_INVITATIONS",
                "You have sent too many invitations recently. Please try again later.",
                HttpStatus.TOO_MANY_REQUESTS
        );
    }
}

