package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class UnknownProgramFamilyException extends AppException {
    public UnknownProgramFamilyException() {
        super(
                "UNKNOWN_PROGRAM_FAMILY",
                "The selected Program Family does not exist.",
                HttpStatus.BAD_REQUEST
        );
    }
}
