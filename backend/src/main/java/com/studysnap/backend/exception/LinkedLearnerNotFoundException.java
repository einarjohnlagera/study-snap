package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class LinkedLearnerNotFoundException extends AppException {
    public LinkedLearnerNotFoundException() {
        super("LINKED_LEARNER_NOT_FOUND", "Linked learner invitation not found.", HttpStatus.NOT_FOUND);
    }
}
