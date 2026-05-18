package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidLongExamSourceException extends AppException {
    public InvalidLongExamSourceException() {
        super(
                "INVALID_LONG_EXAM_SOURCE",
                "Long Exam source notes must be owned by you and share the same subject.",
                HttpStatus.BAD_REQUEST
        );
    }
}
