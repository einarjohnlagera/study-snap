package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class SavedFilterNotFoundException extends AppException {
    public SavedFilterNotFoundException() {
        super("SAVED_FILTER_NOT_FOUND", "Saved filter not found.", HttpStatus.NOT_FOUND);
    }
}
