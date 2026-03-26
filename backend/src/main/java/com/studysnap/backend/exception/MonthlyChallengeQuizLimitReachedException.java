package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class MonthlyChallengeQuizLimitReachedException extends AppException {
    public MonthlyChallengeQuizLimitReachedException() {
        super(
                "MONTHLY_CHALLENGE_QUIZ_LIMIT_REACHED",
                "You've reached your monthly Challenge Quiz limit.",
                HttpStatus.FORBIDDEN
        );
    }
}
