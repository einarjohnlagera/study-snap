package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class CourseProgramNotFoundException extends AppException {
    public CourseProgramNotFoundException() {
        super("COURSE_PROGRAM_NOT_FOUND", "Course / Program not found.", HttpStatus.NOT_FOUND);
    }
}
