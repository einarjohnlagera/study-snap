package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidChallengeQuizDifficultyException extends AppException {
    public InvalidChallengeQuizDifficultyException() {
        super("INVALID_CHALLENGE_QUIZ_DIFFICULTY", "Difficulty must be easy, medium, or hard.", HttpStatus.BAD_REQUEST);
    }
}
