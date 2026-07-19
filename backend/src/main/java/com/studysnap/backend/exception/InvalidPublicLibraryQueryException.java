package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidPublicLibraryQueryException extends AppException {
    public InvalidPublicLibraryQueryException(String parameterName) {
        super(
                "INVALID_PUBLIC_LIBRARY_QUERY",
                "Invalid public-library " + parameterName + " value.",
                HttpStatus.BAD_REQUEST
        );
    }
}
