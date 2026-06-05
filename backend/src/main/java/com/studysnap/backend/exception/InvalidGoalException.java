package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidGoalException extends AppException {
    private static final String CODE = "INVALID_GOAL";
    private static final String BLANK_MESSAGE = "Goal cannot be blank. Pass null to clear your goal.";
    private static final String TOO_LONG_MESSAGE = "Goal value is too long.";

    private InvalidGoalException(String message) {
        super(CODE, message, HttpStatus.BAD_REQUEST);
    }

    public static InvalidGoalException blank() {
        return new InvalidGoalException(BLANK_MESSAGE);
    }

    public static InvalidGoalException tooLong() {
        return new InvalidGoalException(TOO_LONG_MESSAGE);
    }
}
