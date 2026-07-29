package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class AskCompanionSessionEndedException extends AppException {
    public AskCompanionSessionEndedException() {
        super(
                "ASK_COMPANION_SESSION_ENDED",
                "This Ask Companion conversation has ended. Start a new conversation to continue.",
                HttpStatus.CONFLICT
        );
    }
}
