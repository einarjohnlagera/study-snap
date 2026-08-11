package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidExamGoalSlugException extends AppException {
    public InvalidExamGoalSlugException() {
        super(
                "INVALID_EXAM_GOAL_SLUG",
                "Exam goal must be one of: ale, pnle, let, cpale.",
                HttpStatus.BAD_REQUEST
        );
    }
}
