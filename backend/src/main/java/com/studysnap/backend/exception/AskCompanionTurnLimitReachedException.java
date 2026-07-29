package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class AskCompanionTurnLimitReachedException extends AppException {
    public AskCompanionTurnLimitReachedException() {
        super(
                "ASK_COMPANION_TURN_LIMIT_REACHED",
                "This conversation has reached its 6-question limit. Start a new conversation to keep asking.",
                HttpStatus.CONFLICT
        );
    }
}
