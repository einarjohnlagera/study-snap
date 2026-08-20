package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class LinkedLearnerBirthYearCorrectionNotAllowedException extends AppException {
    public LinkedLearnerBirthYearCorrectionNotAllowedException() {
        super(
                "LINKED_LEARNER_BIRTH_YEAR_CORRECTION_NOT_ALLOWED",
                "A birth year can only be corrected after it is recorded through a learning connection.",
                HttpStatus.BAD_REQUEST
        );
    }
}
