package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class QuickReviewSessionAnchorException extends AppException {
    public QuickReviewSessionAnchorException() {
        super(
                "INVALID_QUIZ_SESSION_ANCHOR",
                "Quiz session anchor is invalid.",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
