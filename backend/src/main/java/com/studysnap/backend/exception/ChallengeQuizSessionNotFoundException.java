package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class ChallengeQuizSessionNotFoundException extends AppException {
    public ChallengeQuizSessionNotFoundException() {
        super("SESSION_NOT_FOUND", "Challenge Quiz session not found.", HttpStatus.NOT_FOUND);
    }
}
