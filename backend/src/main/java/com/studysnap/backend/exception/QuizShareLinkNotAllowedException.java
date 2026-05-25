package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class QuizShareLinkNotAllowedException extends AppException {
    public QuizShareLinkNotAllowedException() {
        super(
                "QUIZ_SHARE_LINK_NOT_ALLOWED",
                "Shareable quiz links are available for Teacher and Admin accounts only.",
                HttpStatus.FORBIDDEN
        );
    }
}
