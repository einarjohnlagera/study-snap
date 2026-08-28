package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class LinkedLearnerRelationshipAlreadyExistsException extends AppException {
    public LinkedLearnerRelationshipAlreadyExistsException() {
        super(
                "LINKED_LEARNER_RELATIONSHIP_ALREADY_EXISTS",
                "You already have a pending or active connection with this person.",
                HttpStatus.CONFLICT
        );
    }
}

