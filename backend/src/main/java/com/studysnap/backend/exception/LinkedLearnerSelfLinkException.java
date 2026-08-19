package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class LinkedLearnerSelfLinkException extends AppException {
    public LinkedLearnerSelfLinkException() {
        super("LINKED_LEARNER_SELF_LINK", "You cannot link your account to itself.", HttpStatus.BAD_REQUEST);
    }
}
