package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class NotificationNotFoundException extends AppException {
    public NotificationNotFoundException() {
        super("NOTIFICATION_NOT_FOUND", "Notification not found.", HttpStatus.NOT_FOUND);
    }
}
