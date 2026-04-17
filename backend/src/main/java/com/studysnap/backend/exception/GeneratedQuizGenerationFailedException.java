package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class GeneratedQuizGenerationFailedException extends AppException {
    public GeneratedQuizGenerationFailedException() {
        super(
                "GENERATED_QUIZ_GENERATION_FAILED",
                "We couldn't generate the quiz this time. Please try again.",
                HttpStatus.BAD_GATEWAY
        );
    }
}
