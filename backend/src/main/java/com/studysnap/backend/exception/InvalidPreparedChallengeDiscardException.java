package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidPreparedChallengeDiscardException extends AppException {
    public InvalidPreparedChallengeDiscardException() {
        super("INVALID_PREPARED_CHALLENGE_DISCARD", "Your prepared quiz has changed. Please check it and try again.", HttpStatus.CONFLICT);
    }
}
