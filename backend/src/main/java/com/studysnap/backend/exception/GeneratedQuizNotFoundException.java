package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class GeneratedQuizNotFoundException extends AppException {
    public GeneratedQuizNotFoundException() {
        super(
                "GENERATED_QUIZ_NOT_FOUND",
                "Generated quiz not found.",
                HttpStatus.NOT_FOUND
        );
    }
}
