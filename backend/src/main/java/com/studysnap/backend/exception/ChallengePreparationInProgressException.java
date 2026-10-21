package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class ChallengePreparationInProgressException extends AppException {
    public ChallengePreparationInProgressException() {
        super("PREPARATION_IN_PROGRESS", "This Challenge Quiz is still being prepared.", HttpStatus.CONFLICT);
    }
}
