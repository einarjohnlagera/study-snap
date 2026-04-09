package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidChallengeQuizModeException extends AppException {
    public InvalidChallengeQuizModeException() {
        super("INVALID_CHALLENGE_QUIZ_MODE", "Challenge Quiz mode must be challenge or board_exam.", HttpStatus.BAD_REQUEST);
    }
}
