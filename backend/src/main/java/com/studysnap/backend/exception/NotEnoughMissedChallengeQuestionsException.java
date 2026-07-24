package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class NotEnoughMissedChallengeQuestionsException extends AppException {
    public NotEnoughMissedChallengeQuestionsException() {
        super(
                "NOT_ENOUGH_MISSED_CHALLENGE_QUESTIONS",
                "Answer at least 3 Challenge Quiz questions incorrectly before redoing missed questions.",
                HttpStatus.CONFLICT
        );
    }
}
