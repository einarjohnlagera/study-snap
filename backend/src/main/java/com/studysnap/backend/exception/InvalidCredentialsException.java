package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends AppException {
    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Invalid email, username, or password.", HttpStatus.UNAUTHORIZED);
    }
}
