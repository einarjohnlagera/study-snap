package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidAdaptivePracticeAnswerException extends AppException {
    public InvalidAdaptivePracticeAnswerException(String message) {
        super("INVALID_ADAPTIVE_PRACTICE_ANSWER", message, HttpStatus.BAD_REQUEST);
    }
}
