package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class CourseProgramSelectionRequiredException extends AppException {
    private static final String CODE = "COURSE_PROGRAM_SELECTION_REQUIRED";
    private static final String MESSAGE = "Choose at least one course or program.";

    public CourseProgramSelectionRequiredException() {
        super(CODE, MESSAGE, HttpStatus.BAD_REQUEST);
    }
}
