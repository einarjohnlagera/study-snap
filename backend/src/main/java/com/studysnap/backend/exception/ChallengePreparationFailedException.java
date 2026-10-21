package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class ChallengePreparationFailedException extends AppException {
    public ChallengePreparationFailedException() {
        super("PREPARATION_FAILED", "This Challenge Quiz could not be prepared.", HttpStatus.CONFLICT);
    }
}
