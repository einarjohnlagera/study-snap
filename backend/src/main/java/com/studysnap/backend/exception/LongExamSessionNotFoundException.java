package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class LongExamSessionNotFoundException extends AppException {
    public LongExamSessionNotFoundException() {
        super("LONG_EXAM_SESSION_NOT_FOUND", "Long Exam session not found.", HttpStatus.NOT_FOUND);
    }
}
