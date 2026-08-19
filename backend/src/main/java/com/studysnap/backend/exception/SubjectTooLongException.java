package com.studysnap.backend.exception;

import com.studysnap.backend.util.NoteMetadataBounds;
import org.springframework.http.HttpStatus;

public class SubjectTooLongException extends AppException {
    public SubjectTooLongException() {
        super("SUBJECT_TOO_LONG", NoteMetadataBounds.SUBJECT_TOO_LONG_MESSAGE, HttpStatus.BAD_REQUEST);
    }
}
