package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class AnnouncementNotEndableException extends AppException {
    public AnnouncementNotEndableException() {
        super(
                "ANNOUNCEMENT_NOT_ENDABLE",
                "Only a published announcement can be ended.",
                HttpStatus.CONFLICT
        );
    }
}
