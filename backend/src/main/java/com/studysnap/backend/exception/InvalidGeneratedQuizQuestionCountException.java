package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidGeneratedQuizQuestionCountException extends AppException {
    public InvalidGeneratedQuizQuestionCountException() {
        super(
                "INVALID_GENERATED_QUIZ_QUESTION_COUNT",
                "Question count must be 10, 20, or 30.",
                HttpStatus.BAD_REQUEST
        );
    }
}
