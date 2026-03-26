package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class StudyPackNotFoundException extends AppException {
    public StudyPackNotFoundException() {
        super("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND);
    }
}
