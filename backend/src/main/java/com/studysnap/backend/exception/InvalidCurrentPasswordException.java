package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidCurrentPasswordException extends AppException {
    public InvalidCurrentPasswordException() {
        super("INVALID_CURRENT_PASSWORD", "Current password is incorrect.", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
