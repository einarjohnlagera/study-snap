package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class DraftNotFoundException extends AppException {
    public DraftNotFoundException() {
        super("DRAFT_NOT_FOUND", "Draft not found.", HttpStatus.NOT_FOUND);
    }
}
