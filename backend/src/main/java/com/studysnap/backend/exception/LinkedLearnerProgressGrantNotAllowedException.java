package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class LinkedLearnerProgressGrantNotAllowedException extends AppException {
    public LinkedLearnerProgressGrantNotAllowedException() {
        super(
                "LINKED_LEARNER_PROGRESS_GRANT_NOT_ALLOWED",
                "Only the learner can share progress on this connection.",
                HttpStatus.FORBIDDEN
        );
    }
}
