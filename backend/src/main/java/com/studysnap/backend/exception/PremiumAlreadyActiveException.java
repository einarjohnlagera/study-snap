package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class PremiumAlreadyActiveException extends AppException {
    public PremiumAlreadyActiveException() {
        super(
                "PLAN_ALREADY_PREMIUM",
                "Your Premium plan is already active.",
                HttpStatus.CONFLICT
        );
    }
}
