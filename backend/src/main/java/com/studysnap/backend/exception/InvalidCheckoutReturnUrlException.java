package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidCheckoutReturnUrlException extends AppException {
    private static final String CODE = "INVALID_CHECKOUT_RETURN_URL";
    private static final String MESSAGE = "Checkout return URL must be a safe internal path.";

    public InvalidCheckoutReturnUrlException() {
        super(CODE, MESSAGE, HttpStatus.BAD_REQUEST);
    }
}
