package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class QuizShareLinkNotFoundException extends AppException {
    public QuizShareLinkNotFoundException() {
        super(
                "QUIZ_SHARE_LINK_NOT_FOUND",
                "This quiz link is no longer active.",
                HttpStatus.NOT_FOUND
        );
    }
}
