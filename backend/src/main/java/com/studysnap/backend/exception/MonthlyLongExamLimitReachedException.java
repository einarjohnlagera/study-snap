package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class MonthlyLongExamLimitReachedException extends AppException {
    public MonthlyLongExamLimitReachedException() {
        super(
                "MONTHLY_LONG_EXAM_LIMIT_REACHED",
                "You've reached your monthly Long Exam limit.",
                HttpStatus.FORBIDDEN
        );
    }
}
