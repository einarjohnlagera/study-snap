package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidSavedFilterRequestException extends AppException {
    public InvalidSavedFilterRequestException(String message) {
        super("INVALID_SAVED_FILTER_REQUEST", message, HttpStatus.BAD_REQUEST);
    }
}
