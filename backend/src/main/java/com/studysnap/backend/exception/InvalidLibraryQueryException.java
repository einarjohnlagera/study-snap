package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidLibraryQueryException extends AppException {
    public InvalidLibraryQueryException(String parameterName) {
        super(
                "INVALID_LIBRARY_QUERY",
                "Invalid private-library " + parameterName + " value.",
                HttpStatus.BAD_REQUEST
        );
    }
}
