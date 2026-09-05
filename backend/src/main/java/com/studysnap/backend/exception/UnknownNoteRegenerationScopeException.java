package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

/** The regeneration request carried a scope value the server does not recognise. */
public class UnknownNoteRegenerationScopeException extends AppException {
    public UnknownNoteRegenerationScopeException() {
        super(
                "UNKNOWN_NOTE_REGENERATION_SCOPE",
                "Unknown regeneration scope.",
                HttpStatus.BAD_REQUEST
        );
    }
}
