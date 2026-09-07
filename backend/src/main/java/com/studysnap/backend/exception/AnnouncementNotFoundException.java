package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class AnnouncementNotFoundException extends AppException {
    public AnnouncementNotFoundException() {
        super("ANNOUNCEMENT_NOT_FOUND", "Announcement not found.", HttpStatus.NOT_FOUND);
    }
}
