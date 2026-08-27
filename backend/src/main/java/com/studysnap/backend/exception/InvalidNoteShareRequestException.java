package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidNoteShareRequestException extends AppException {
    public InvalidNoteShareRequestException() {
        super(
                "INVALID_NOTE_SHARE_REQUEST",
                "Every selected connection must be accepted and belong to your account.",
                HttpStatus.BAD_REQUEST
        );
    }
}
