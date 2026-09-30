package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class QuickReviewSessionStaleException extends AppException {
    public QuickReviewSessionStaleException() {
        super(
                "QUICK_REVIEW_SESSION_STALE",
                "This Quick Review belongs to an older version of the Study Pack. Restart to continue.",
                HttpStatus.CONFLICT
        );
    }
}
