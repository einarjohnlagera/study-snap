package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class LinkedLearnerNotAllowedException extends AppException {
    public LinkedLearnerNotAllowedException() {
        super("LINKED_LEARNER_NOT_ALLOWED", "You cannot perform this action on this link.", HttpStatus.FORBIDDEN);
    }
}
