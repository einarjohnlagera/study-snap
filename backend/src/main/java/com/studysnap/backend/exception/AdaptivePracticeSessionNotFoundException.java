package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class AdaptivePracticeSessionNotFoundException extends AppException {
    public AdaptivePracticeSessionNotFoundException() {
        super("SESSION_NOT_FOUND", "Adaptive Practice session not found.", HttpStatus.NOT_FOUND);
    }
}
