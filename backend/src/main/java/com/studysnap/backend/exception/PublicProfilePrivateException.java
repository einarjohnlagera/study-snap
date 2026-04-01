package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class PublicProfilePrivateException extends AppException {
    public PublicProfilePrivateException() {
        super("PUBLIC_PROFILE_PRIVATE", "This profile is private.", HttpStatus.FORBIDDEN);
    }
}
