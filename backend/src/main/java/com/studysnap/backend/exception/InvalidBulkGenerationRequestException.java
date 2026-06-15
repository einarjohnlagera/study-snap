package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidBulkGenerationRequestException extends AppException {
    public InvalidBulkGenerationRequestException(String message) {
        super("INVALID_BULK_GENERATION_REQUEST", message, HttpStatus.BAD_REQUEST);
    }
}
