package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class ChallengeQuizQuestionBankUnavailableException extends AppException {
    public ChallengeQuizQuestionBankUnavailableException() {
        super(
                "CHALLENGE_QUIZ_QUESTION_BANK_UNAVAILABLE",
                "Could not load your missed questions right now. Please try again.",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }
}
