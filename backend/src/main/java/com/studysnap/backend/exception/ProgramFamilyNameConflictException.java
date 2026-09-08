package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class ProgramFamilyNameConflictException extends AppException {
    private static final String ERROR_CODE = "PROGRAM_FAMILY_NAME_CONFLICT";

    public ProgramFamilyNameConflictException(String existingFamilyName) {
        super(
                ERROR_CODE,
                "A Program Family named \"" + existingFamilyName + "\" already exists.",
                existingFamilyName,
                HttpStatus.CONFLICT
        );
    }
}
