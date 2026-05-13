package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class LongExamSessionNotInProgressException extends AppException {
    public LongExamSessionNotInProgressException() {
        super("LONG_EXAM_SESSION_NOT_IN_PROGRESS", "Long Exam session is not in progress.", HttpStatus.BAD_REQUEST);
    }
}
