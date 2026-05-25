package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class QuizShareLinkTokenGenerationException extends AppException {
    public QuizShareLinkTokenGenerationException() {
        super(
                "QUIZ_SHARE_LINK_TOKEN_GENERATION_FAILED",
                "Could not create a shareable quiz link. Please try again.",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
