package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class CollectionNotPublishableException extends AppException {
    public CollectionNotPublishableException(String message) {
        super("COLLECTION_NOT_PUBLISHABLE", message, HttpStatus.BAD_REQUEST);
    }
}
