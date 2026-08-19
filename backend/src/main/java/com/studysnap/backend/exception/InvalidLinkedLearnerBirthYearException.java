package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidLinkedLearnerBirthYearException extends AppException {
    public InvalidLinkedLearnerBirthYearException() {
        super("INVALID_LINKED_LEARNER_BIRTH_YEAR", "Enter a valid birth year.", HttpStatus.BAD_REQUEST);
    }
}
