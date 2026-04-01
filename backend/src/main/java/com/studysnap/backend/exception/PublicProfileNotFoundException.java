package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class PublicProfileNotFoundException extends AppException {
    public PublicProfileNotFoundException() {
        super("PUBLIC_PROFILE_NOT_FOUND", "Public profile not found.", HttpStatus.NOT_FOUND);
    }
}
