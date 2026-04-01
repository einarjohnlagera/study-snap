package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class NoteNotFoundException extends AppException {
    public NoteNotFoundException() {
        super("NOTE_NOT_FOUND", "Note not found.", HttpStatus.NOT_FOUND);
    }
}
