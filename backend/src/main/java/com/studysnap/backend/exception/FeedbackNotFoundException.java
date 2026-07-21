package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class FeedbackNotFoundException extends AppException {
    public FeedbackNotFoundException() {
        super("FEEDBACK_NOT_FOUND", "Feedback not found.", HttpStatus.NOT_FOUND);
    }
}
