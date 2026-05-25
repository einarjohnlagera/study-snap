package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidSharedQuizAnswersException extends AppException {
    public InvalidSharedQuizAnswersException() {
        super(
                "INVALID_QUIZ_ANSWERS",
                "answers length must match question count",
                HttpStatus.BAD_REQUEST
        );
    }
}
