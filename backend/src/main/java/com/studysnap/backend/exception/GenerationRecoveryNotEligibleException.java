package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class GenerationRecoveryNotEligibleException extends AppException {
    public GenerationRecoveryNotEligibleException() {
        super(
                "GENERATION_RECOVERY_NOT_ELIGIBLE",
                "This note isn't eligible for manual recovery right now.",
                HttpStatus.CONFLICT
        );
    }
}
