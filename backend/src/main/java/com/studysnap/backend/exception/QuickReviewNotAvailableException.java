package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class QuickReviewNotAvailableException extends AppException {
    public QuickReviewNotAvailableException() {
        super(
                "QUICK_REVIEW_NOT_AVAILABLE",
                "Quick Review is not available for this Study Pack yet.",
                HttpStatus.BAD_REQUEST
        );
    }
}
