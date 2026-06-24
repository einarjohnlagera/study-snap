package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidResendWebhookSignatureException extends AppException {
    public InvalidResendWebhookSignatureException() {
        super(
                "INVALID_RESEND_WEBHOOK_SIGNATURE",
                "Could not verify the Resend webhook signature.",
                HttpStatus.UNAUTHORIZED
        );
    }
}
