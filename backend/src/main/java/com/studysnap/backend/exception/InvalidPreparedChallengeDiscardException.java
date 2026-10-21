package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidPreparedChallengeDiscardException extends AppException {
    public InvalidPreparedChallengeDiscardException() {
        super("INVALID_PREPARED_CHALLENGE_DISCARD", "The prepared quiz cannot be discarded for this request.", HttpStatus.CONFLICT);
    }
}
