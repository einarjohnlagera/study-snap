package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class MultipleExamVersionsNotAllowedForPlanException extends AppException {
    private static final String ERROR_CODE = "MULTIPLE_EXAM_VERSIONS_NOT_ALLOWED";
    private static final String MESSAGE = "Plus unlocks multiple exam versions for anti-cheating.";
    private static final String DETAILS = "versionCount=teacher-plus";
    private static final String ACTION = "UPGRADE_TO_PLUS";

    public MultipleExamVersionsNotAllowedForPlanException() {
        super(ERROR_CODE, MESSAGE, DETAILS, ACTION, HttpStatus.PAYMENT_REQUIRED);
    }
}
