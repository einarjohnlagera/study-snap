package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class ChallengePreparationInProgressException extends AppException {
    public ChallengePreparationInProgressException() {
        super("PREPARATION_IN_PROGRESS", "Your quiz is still getting ready. We'll let you know in NoteLib when it's ready.", HttpStatus.CONFLICT);
    }
}
