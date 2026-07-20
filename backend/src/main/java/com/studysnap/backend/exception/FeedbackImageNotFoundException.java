package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class FeedbackImageNotFoundException extends AppException {
    public FeedbackImageNotFoundException() {
        super("FEEDBACK_IMAGE_NOT_FOUND", "Feedback screenshot not found.", HttpStatus.NOT_FOUND);
    }
}
