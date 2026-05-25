package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class RefundFailedException extends AppException {
    public RefundFailedException() {
        super(
                "REFUND_FAILED",
                "Xendit could not process the refund. Check the Xendit dashboard.",
                HttpStatus.BAD_GATEWAY
        );
    }
}
