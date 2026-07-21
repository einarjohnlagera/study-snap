package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidFeedbackImageException extends AppException {
    public InvalidFeedbackImageException(String message) {
        super("INVALID_FEEDBACK_IMAGE", message, HttpStatus.BAD_REQUEST);
    }
}
