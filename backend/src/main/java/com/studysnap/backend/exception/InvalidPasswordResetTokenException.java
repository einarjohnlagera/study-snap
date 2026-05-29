package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidPasswordResetTokenException extends AppException {
    public InvalidPasswordResetTokenException() {
        super("INVALID_RESET_TOKEN", "This password reset link is invalid or has expired.", HttpStatus.BAD_REQUEST);
    }
}
