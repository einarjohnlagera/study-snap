package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class AnnouncementNotPublishableException extends AppException {
    public AnnouncementNotPublishableException() {
        super(
                "ANNOUNCEMENT_NOT_PUBLISHABLE",
                "This announcement has ended and cannot be published again.",
                null,
                "Publish a replacement announcement instead.",
                HttpStatus.CONFLICT
        );
    }
}
