package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class RefundNotEligibleException extends AppException {
    public RefundNotEligibleException(String message) {
        super("REFUND_NOT_ELIGIBLE", message, HttpStatus.CONFLICT);
    }
}
