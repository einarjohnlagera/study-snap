package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class AuthenticationRequiredException extends AppException {
    public AuthenticationRequiredException() {
        super("AUTHENTICATION_REQUIRED", "Authentication required.", HttpStatus.UNAUTHORIZED);
    }
}
