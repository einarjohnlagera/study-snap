package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * ⚠️ THE MESSAGE ALWAYS NAMES THE FIELD. An admin-authored CTA is rendered in every user's inbox, so
 * a rejection that does not say WHICH field failed invites the author to retry blind.
 */
public class InvalidAnnouncementRequestException extends AppException {
    public InvalidAnnouncementRequestException(String field, String reason) {
        super("INVALID_ANNOUNCEMENT_REQUEST", field + ": " + reason, HttpStatus.BAD_REQUEST);
    }
}
