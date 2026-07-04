package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class MemorizationNotAvailableException extends AppException {
    public MemorizationNotAvailableException() {
        super("MEMORIZATION_NOT_AVAILABLE", "Memorization is not available for this profile.", HttpStatus.FORBIDDEN);
    }
}
