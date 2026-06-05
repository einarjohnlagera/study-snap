package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidExamGoalException extends AppException {
    public InvalidExamGoalException() {
        super("INVALID_EXAM_GOAL", "Unknown exam goal. Valid values: ale, pnle, let.", HttpStatus.BAD_REQUEST);
    }
}
