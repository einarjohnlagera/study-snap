package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class NotEnoughNewQuestionsException extends AppException {
    public NotEnoughNewQuestionsException() {
        super("NOT_ENOUGH_NEW_QUESTIONS",
                "Not enough new questions could be generated for this note.",
                HttpStatus.CONFLICT);
    }
}
