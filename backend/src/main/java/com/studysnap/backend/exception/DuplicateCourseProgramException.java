package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class DuplicateCourseProgramException extends AppException {
    public DuplicateCourseProgramException() {
        super(
                "DUPLICATE_COURSE_PROGRAM",
                "Applicable Programs cannot contain duplicate course program ids.",
                HttpStatus.BAD_REQUEST
        );
    }
}
