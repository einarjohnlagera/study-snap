package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidNoteLearnerLevelException extends AppException {
    private static final String CODE = "INVALID_NOTE_LEARNER_LEVEL";
    private static final String MESSAGE = "Invalid learnerLevel. Valid values: Grade School, Junior High, Senior High, "
            + "College, Board Exam Review, Professional, Personal Learning.";

    public InvalidNoteLearnerLevelException() {
        super(CODE, MESSAGE, HttpStatus.BAD_REQUEST);
    }
}
