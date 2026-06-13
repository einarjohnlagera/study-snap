package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidCollectionRequestException extends AppException {
    public InvalidCollectionRequestException(String message) {
        super("INVALID_COLLECTION_REQUEST", message, HttpStatus.BAD_REQUEST);
    }
}
