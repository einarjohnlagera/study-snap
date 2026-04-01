package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class QuickReviewSessionNotFoundException extends AppException {
    public QuickReviewSessionNotFoundException() {
        super("SESSION_NOT_FOUND", "Quick Review session not found.", HttpStatus.NOT_FOUND);
    }
}
