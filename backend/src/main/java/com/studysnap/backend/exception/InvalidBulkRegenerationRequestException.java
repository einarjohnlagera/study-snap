package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidBulkRegenerationRequestException extends AppException {
    private static final String CODE = "INVALID_BULK_REGENERATION_REQUEST";

    public InvalidBulkRegenerationRequestException(String message) {
        super(CODE, message, HttpStatus.BAD_REQUEST);
    }
}
