package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidQuickReviewAnswerException extends AppException {
    public InvalidQuickReviewAnswerException(String message) {
        super("INVALID_QUICK_REVIEW_ANSWER", message, HttpStatus.BAD_REQUEST);
    }
}
