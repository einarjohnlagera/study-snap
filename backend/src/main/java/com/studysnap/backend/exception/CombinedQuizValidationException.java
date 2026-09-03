package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class CombinedQuizValidationException extends AppException {
    private CombinedQuizValidationException(String message) {
        super("COMBINED_QUIZ_INVALID", message, HttpStatus.BAD_REQUEST);
    }

    public static CombinedQuizValidationException invalidTitle() {
        return new CombinedQuizValidationException("Provide a title for the combined quiz.");
    }

    public static CombinedQuizValidationException selectionTooLarge() {
        return new CombinedQuizValidationException("The combined quiz selection exceeds the supported limit.");
    }
}
