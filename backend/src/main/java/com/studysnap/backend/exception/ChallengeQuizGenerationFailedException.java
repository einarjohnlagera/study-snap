package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class ChallengeQuizGenerationFailedException extends AppException {
    public ChallengeQuizGenerationFailedException() {
        super(
                "CHALLENGE_QUIZ_GENERATION_FAILED",
                "Could not generate enough unique challenge questions. Please try again.",
                HttpStatus.BAD_GATEWAY
        );
    }
}
