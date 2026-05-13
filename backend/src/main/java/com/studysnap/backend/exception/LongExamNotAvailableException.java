package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class LongExamNotAvailableException extends AppException {
    public LongExamNotAvailableException() {
        super(
                "LONG_EXAM_NOT_AVAILABLE",
                "Long Exam Mode requires a Pro plan.",
                HttpStatus.FORBIDDEN
        );
    }
}
