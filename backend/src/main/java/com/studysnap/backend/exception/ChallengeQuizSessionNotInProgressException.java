package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class ChallengeQuizSessionNotInProgressException extends AppException {
    public ChallengeQuizSessionNotInProgressException() {
        super("SESSION_NOT_IN_PROGRESS", "Challenge Quiz session is already completed.", HttpStatus.BAD_REQUEST);
    }
}
