package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidReviewDayException extends AppException {
    private static final String CODE = "INVALID_REVIEW_DAY";
    private static final String MESSAGE = "Review days must use valid weekday names.";

    public InvalidReviewDayException() {
        super(CODE, MESSAGE, HttpStatus.BAD_REQUEST);
    }
}
