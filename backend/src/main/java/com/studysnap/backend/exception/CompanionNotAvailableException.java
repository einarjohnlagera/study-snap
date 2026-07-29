package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class CompanionNotAvailableException extends AppException {
    public CompanionNotAvailableException() {
        super(
                "COMPANION_NOT_AVAILABLE",
                "Ask Companion is not available for this Review Set.",
                HttpStatus.CONFLICT
        );
    }
}
