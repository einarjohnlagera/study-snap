package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class CollectionItemNotFoundException extends AppException {
    public CollectionItemNotFoundException() {
        super("COLLECTION_ITEM_NOT_FOUND", "Collection item not found.", HttpStatus.NOT_FOUND);
    }
}
