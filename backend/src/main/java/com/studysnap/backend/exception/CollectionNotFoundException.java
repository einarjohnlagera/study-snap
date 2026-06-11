package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class CollectionNotFoundException extends AppException {
    public CollectionNotFoundException() {
        super("COLLECTION_NOT_FOUND", "Collection not found.", HttpStatus.NOT_FOUND);
    }
}
