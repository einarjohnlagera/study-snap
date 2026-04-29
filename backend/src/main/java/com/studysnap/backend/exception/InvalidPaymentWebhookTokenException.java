package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidPaymentWebhookTokenException extends AppException {
    public InvalidPaymentWebhookTokenException() {
        super(
                "INVALID_PAYMENT_WEBHOOK_TOKEN",
                "Could not verify the payment webhook token.",
                HttpStatus.FORBIDDEN
        );
    }
}
