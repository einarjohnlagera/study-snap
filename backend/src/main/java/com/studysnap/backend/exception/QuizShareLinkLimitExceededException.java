package com.studysnap.backend.exception;

import com.studysnap.backend.entity.PlanType;
import org.springframework.http.HttpStatus;

public class QuizShareLinkLimitExceededException extends AppException {
    private static final String ERROR_CODE = "QUIZ_SHARE_LINK_LIMIT_EXCEEDED";
    private static final String FREE_MESSAGE = "You’ve reached your monthly shareable quiz link limit.";
    private static final String PLUS_MESSAGE = "You’ve reached your monthly shareable quiz link limit for Plus.";
    private static final String DEFAULT_MESSAGE = "You’ve reached your monthly shareable quiz link limit for this billing cycle.";

    private QuizShareLinkLimitExceededException(String message) {
        super(ERROR_CODE, message, HttpStatus.PAYMENT_REQUIRED);
    }

    public static QuizShareLinkLimitExceededException forPlan(PlanType planType) {
        if (planType == PlanType.FREE) {
            return new QuizShareLinkLimitExceededException(FREE_MESSAGE);
        }
        if (planType == PlanType.PLUS) {
            return new QuizShareLinkLimitExceededException(PLUS_MESSAGE);
        }
        return new QuizShareLinkLimitExceededException(DEFAULT_MESSAGE);
    }
}
