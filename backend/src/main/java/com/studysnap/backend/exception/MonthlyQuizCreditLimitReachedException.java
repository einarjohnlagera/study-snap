package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class MonthlyQuizCreditLimitReachedException extends AppException {
    public MonthlyQuizCreditLimitReachedException() {
        super(
                "MONTHLY_QUIZ_CREDIT_LIMIT_REACHED",
                "You've reached your monthly quiz credit limit.",
                HttpStatus.FORBIDDEN
        );
    }
}
