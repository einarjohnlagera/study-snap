package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class LinkedLearnerProgressNotFoundException extends AppException {
    public LinkedLearnerProgressNotFoundException() {
        super(
                "LINKED_LEARNER_PROGRESS_NOT_FOUND",
                "Linked learner progress is not available.",
                HttpStatus.NOT_FOUND
        );
    }
}
