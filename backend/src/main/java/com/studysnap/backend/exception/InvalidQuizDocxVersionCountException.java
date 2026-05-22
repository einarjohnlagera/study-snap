package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidQuizDocxVersionCountException extends AppException {
    private static final String ERROR_CODE = "INVALID_QUIZ_DOCX_VERSION_COUNT";
    private static final String MESSAGE = "DOCX exam version count must be 1, 2, or 3.";

    public InvalidQuizDocxVersionCountException() {
        super(ERROR_CODE, MESSAGE, HttpStatus.BAD_REQUEST);
    }
}
