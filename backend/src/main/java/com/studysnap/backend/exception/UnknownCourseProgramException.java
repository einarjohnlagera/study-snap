package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class UnknownCourseProgramException extends AppException {
    public UnknownCourseProgramException() {
        super(
                "UNKNOWN_COURSE_PROGRAM",
                "One or more course program ids do not exist.",
                HttpStatus.BAD_REQUEST
        );
    }
}
