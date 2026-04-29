package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class PaymentCheckoutUnavailableException extends AppException {
    public PaymentCheckoutUnavailableException(String message) {
        super(
                "PAYMENT_CHECKOUT_UNAVAILABLE",
                message,
                HttpStatus.BAD_GATEWAY
        );
    }
}
