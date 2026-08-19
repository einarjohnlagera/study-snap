package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class QuizShareLinkNotAllowedException extends AppException {
    public QuizShareLinkNotAllowedException() {
        super(
                "QUIZ_SHARE_LINK_NOT_ALLOWED",
                "You can only manage share links for your own quizzes.",
                HttpStatus.FORBIDDEN
        );
    }
}
