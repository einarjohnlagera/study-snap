package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class CourseProgramCatalogNameConflictException extends AppException {
    private static final String ERROR_CODE = "COURSE_PROGRAM_CATALOG_NAME_CONFLICT";

    public CourseProgramCatalogNameConflictException(String existingProgramName) {
        super(
                ERROR_CODE,
                "A Course / Program named \"" + existingProgramName + "\" already exists.",
                existingProgramName,
                HttpStatus.CONFLICT
        );
    }
}
