package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class SharedNoteNotFoundException extends AppException {
    public SharedNoteNotFoundException() {
        super("SHARED_NOTE_NOT_FOUND", "This note is no longer shared with you.", HttpStatus.NOT_FOUND);
    }
}
