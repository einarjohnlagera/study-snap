package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidChallengeQuizResultException extends AppException {
    public InvalidChallengeQuizResultException() {
        super("INVALID_SESSION_RESULT", "Correct answers cannot exceed total questions.", HttpStatus.BAD_REQUEST);
    }
}
