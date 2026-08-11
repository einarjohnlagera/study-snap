package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class CourseProgramCatalogWriteConflictException extends AppException {
    public CourseProgramCatalogWriteConflictException() {
        super(
                "COURSE_PROGRAM_CATALOG_WRITE_CONFLICT",
                "The catalog changed while this Course / Program was being added. Review the current catalog and try again.",
                HttpStatus.CONFLICT
        );
    }
}
