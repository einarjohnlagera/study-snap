package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends AppException {
    public InvalidRefreshTokenException() {
        super("INVALID_REFRESH_TOKEN", "Invalid refresh token.", HttpStatus.UNAUTHORIZED);
    }
}
