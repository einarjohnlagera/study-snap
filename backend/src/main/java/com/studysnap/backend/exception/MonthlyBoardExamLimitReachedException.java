package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class MonthlyBoardExamLimitReachedException extends AppException {
    public MonthlyBoardExamLimitReachedException() {
        super(
                "MONTHLY_BOARD_EXAM_LIMIT_REACHED",
                "You've reached your monthly Board Exam limit.",
                HttpStatus.FORBIDDEN
        );
    }
}
