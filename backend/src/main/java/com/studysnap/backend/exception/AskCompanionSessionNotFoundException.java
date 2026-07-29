package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class AskCompanionSessionNotFoundException extends AppException {
    public AskCompanionSessionNotFoundException() {
        super("ASK_COMPANION_SESSION_NOT_FOUND", "Ask Companion session not found.", HttpStatus.NOT_FOUND);
    }
}
