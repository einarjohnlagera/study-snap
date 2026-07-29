package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class AskCompanionUnavailableException extends AppException {
    public AskCompanionUnavailableException() {
        super(
                "ASK_COMPANION_UNAVAILABLE",
                "Ask Companion is temporarily unavailable. Please try again.",
                HttpStatus.BAD_GATEWAY
        );
    }
}
