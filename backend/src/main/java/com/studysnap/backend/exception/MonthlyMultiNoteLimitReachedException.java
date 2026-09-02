package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

/** The per-plan allowance for Challenge Quiz sessions using more than one source note. */
public class MonthlyMultiNoteLimitReachedException extends AppException {
    public MonthlyMultiNoteLimitReachedException(int monthlyLimit) {
        super(
                "MONTHLY_MULTI_NOTE_LIMIT_REACHED",
                "You've used all " + monthlyLimit + " multi-note Challenge Quiz sessions in this billing period.",
                HttpStatus.FORBIDDEN
        );
    }
}
