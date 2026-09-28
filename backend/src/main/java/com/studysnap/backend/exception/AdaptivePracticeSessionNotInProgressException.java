package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class AdaptivePracticeSessionNotInProgressException extends AppException {
    public AdaptivePracticeSessionNotInProgressException() {
        super(
                "ADAPTIVE_PRACTICE_SESSION_NOT_IN_PROGRESS",
                "This Adaptive Practice session is not in progress.",
                HttpStatus.CONFLICT
        );
    }
}
