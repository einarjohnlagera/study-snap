package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class ReviewSetUpdateNotPublishableException extends AppException {
    public ReviewSetUpdateNotPublishableException() {
        super("REVIEW_SET_UPDATE_NOT_PUBLISHABLE", "Only an already-public Official Review Set can publish an update.", HttpStatus.BAD_REQUEST);
    }
}
