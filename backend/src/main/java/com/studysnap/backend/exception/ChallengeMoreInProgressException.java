package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class ChallengeMoreInProgressException extends AppException {
    public ChallengeMoreInProgressException() {
        super("MORE_IN_PROGRESS", "We're already adding questions. They'll appear as soon as they're ready.", HttpStatus.CONFLICT);
    }
}
