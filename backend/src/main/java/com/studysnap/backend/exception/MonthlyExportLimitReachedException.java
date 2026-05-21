package com.studysnap.backend.exception;

import com.studysnap.backend.entity.PlanType;
import org.springframework.http.HttpStatus;

public class MonthlyExportLimitReachedException extends AppException {
    private static final String ERROR_CODE = "MONTHLY_EXPORT_LIMIT_REACHED";
    private static final String FREE_MESSAGE = "You’ve reached your monthly export limit.";
    private static final String PLUS_MESSAGE = "You’ve reached your monthly export limit for Plus.";
    private static final String DEFAULT_MESSAGE = "You’ve reached your monthly export limit for this billing cycle.";

    private MonthlyExportLimitReachedException(String message) {
        super(ERROR_CODE, message, HttpStatus.PAYMENT_REQUIRED);
    }

    public static MonthlyExportLimitReachedException forPlan(PlanType planType) {
        if (planType == PlanType.FREE) {
            return new MonthlyExportLimitReachedException(FREE_MESSAGE);
        }
        if (planType == PlanType.PLUS) {
            return new MonthlyExportLimitReachedException(PLUS_MESSAGE);
        }
        return new MonthlyExportLimitReachedException(DEFAULT_MESSAGE);
    }
}
