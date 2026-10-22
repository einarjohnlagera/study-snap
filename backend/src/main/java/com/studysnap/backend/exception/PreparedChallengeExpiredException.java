package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class PreparedChallengeExpiredException extends AppException {
    public PreparedChallengeExpiredException() {
        super("PREPARED_CHALLENGE_EXPIRED", "This prepared quiz has expired. You can prepare a new one anytime.", HttpStatus.CONFLICT);
    }
}
