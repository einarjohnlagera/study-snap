package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class ChallengeQuizNotAvailableException extends AppException {
    public ChallengeQuizNotAvailableException() {
        super(
                "CHALLENGE_QUIZ_NOT_AVAILABLE",
                "Challenge Quiz session is not available. Please start again.",
                HttpStatus.BAD_REQUEST
        );
    }
}
