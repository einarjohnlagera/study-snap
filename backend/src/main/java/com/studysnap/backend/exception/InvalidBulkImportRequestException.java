package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidBulkImportRequestException extends AppException {
    public InvalidBulkImportRequestException(String message) {
        super("INVALID_BULK_IMPORT_REQUEST", message, HttpStatus.BAD_REQUEST);
    }
}
