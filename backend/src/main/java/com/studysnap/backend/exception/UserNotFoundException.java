package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends AppException {
    public UserNotFoundException() {
        super("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND);
    }
}
