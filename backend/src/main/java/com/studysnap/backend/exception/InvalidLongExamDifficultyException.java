package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidLongExamDifficultyException extends AppException {
    public InvalidLongExamDifficultyException() {
        super("INVALID_LONG_EXAM_DIFFICULTY", "Difficulty must be easy, medium, hard, or mixed.", HttpStatus.BAD_REQUEST);
    }
}
