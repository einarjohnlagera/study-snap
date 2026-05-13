package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class LongExamGenerationFailedException extends AppException {
    public LongExamGenerationFailedException() {
        super(
                "LONG_EXAM_GENERATION_FAILED",
                "Could not generate enough Long Exam questions. Please try again.",
                HttpStatus.BAD_GATEWAY
        );
    }
}
