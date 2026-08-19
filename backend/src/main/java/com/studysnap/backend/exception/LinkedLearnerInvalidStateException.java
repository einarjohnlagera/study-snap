package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class LinkedLearnerInvalidStateException extends AppException {
    public LinkedLearnerInvalidStateException() {
        super("LINKED_LEARNER_INVALID_STATE", "This invitation is no longer pending.", HttpStatus.CONFLICT);
    }
}
