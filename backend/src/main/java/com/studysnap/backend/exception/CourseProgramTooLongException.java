package com.studysnap.backend.exception;

import com.studysnap.backend.util.NoteMetadataBounds;
import org.springframework.http.HttpStatus;

public class CourseProgramTooLongException extends AppException {
    public CourseProgramTooLongException() {
        super("COURSE_PROGRAM_TOO_LONG", NoteMetadataBounds.COURSE_PROGRAM_TOO_LONG_MESSAGE, HttpStatus.BAD_REQUEST);
    }
}
