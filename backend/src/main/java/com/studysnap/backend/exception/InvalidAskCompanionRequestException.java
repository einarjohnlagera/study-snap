package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidAskCompanionRequestException extends AppException {
    public InvalidAskCompanionRequestException(String message) {
        super("INVALID_ASK_COMPANION_REQUEST", message, HttpStatus.BAD_REQUEST);
    }
}
