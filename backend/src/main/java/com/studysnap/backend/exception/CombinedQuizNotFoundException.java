package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class CombinedQuizNotFoundException extends AppException {
    public CombinedQuizNotFoundException() {
        super("COMBINED_QUIZ_NOT_FOUND", "Combined quiz not found.", HttpStatus.NOT_FOUND);
    }
}
