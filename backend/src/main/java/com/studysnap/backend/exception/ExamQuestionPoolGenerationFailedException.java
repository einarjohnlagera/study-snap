package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class ExamQuestionPoolGenerationFailedException extends AppException {
    public ExamQuestionPoolGenerationFailedException() {
        super(
                "EXAM_QUESTION_POOL_GENERATION_FAILED",
                "Could not generate enough exam question pool items.",
                HttpStatus.BAD_GATEWAY
        );
    }
}
