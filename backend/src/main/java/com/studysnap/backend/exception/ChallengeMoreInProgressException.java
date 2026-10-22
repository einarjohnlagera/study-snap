package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class ChallengeMoreInProgressException extends AppException {
    public ChallengeMoreInProgressException() {
        super("MORE_IN_PROGRESS", "Questions are already being added.", HttpStatus.CONFLICT);
    }
}
