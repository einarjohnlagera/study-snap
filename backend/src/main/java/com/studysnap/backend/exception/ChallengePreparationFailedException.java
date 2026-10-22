package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class ChallengePreparationFailedException extends AppException {
    public ChallengePreparationFailedException() {
        super("PREPARATION_FAILED", "We couldn't finish preparing this quiz. Please try again.", HttpStatus.CONFLICT);
    }
}
