package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class LongExamSessionNotPausableException extends AppException {
    public LongExamSessionNotPausableException() {
        super("LONG_EXAM_SESSION_NOT_PAUSABLE", "Only an in-progress Long Exam can be paused.", HttpStatus.BAD_REQUEST);
    }
}
