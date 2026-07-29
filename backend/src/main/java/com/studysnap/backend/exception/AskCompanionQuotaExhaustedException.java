package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class AskCompanionQuotaExhaustedException extends AppException {
    public AskCompanionQuotaExhaustedException() {
        super(
                "ASK_COMPANION_QUOTA_EXHAUSTED",
                "You've reached your monthly Ask Companion session limit. Come back when your usage resets.",
                HttpStatus.FORBIDDEN
        );
    }
}
