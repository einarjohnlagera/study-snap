package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidProgramFamilyNameException extends AppException {
    public InvalidProgramFamilyNameException() {
        super(
                "INVALID_PROGRAM_FAMILY_NAME",
                "Program Family name must be 120 characters or fewer.",
                HttpStatus.BAD_REQUEST
        );
    }
}
