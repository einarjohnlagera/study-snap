package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidUnsubscribeTokenException extends AppException {
    public InvalidUnsubscribeTokenException() {
        super(
                "INVALID_UNSUBSCRIBE_TOKEN",
                "This unsubscribe link is invalid or has expired.",
                HttpStatus.BAD_REQUEST
        );
    }
}
