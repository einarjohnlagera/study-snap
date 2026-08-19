package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class LinkedLearnerBirthYearRequiredException extends AppException {
    public LinkedLearnerBirthYearRequiredException() {
        super("LINKED_LEARNER_BIRTH_YEAR_REQUIRED", "The learner's birth year is required before this invitation can be accepted.", HttpStatus.BAD_REQUEST);
    }
}
