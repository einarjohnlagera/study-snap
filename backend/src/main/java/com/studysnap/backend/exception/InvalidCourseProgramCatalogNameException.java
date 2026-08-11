package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidCourseProgramCatalogNameException extends AppException {
    public InvalidCourseProgramCatalogNameException() {
        super(
                "INVALID_COURSE_PROGRAM_CATALOG_NAME",
                "Course / Program name must be 120 characters or fewer.",
                HttpStatus.BAD_REQUEST
        );
    }
}
