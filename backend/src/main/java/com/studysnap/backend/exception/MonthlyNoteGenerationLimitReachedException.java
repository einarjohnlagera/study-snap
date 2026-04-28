package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class MonthlyNoteGenerationLimitReachedException extends AppException {
    public MonthlyNoteGenerationLimitReachedException() {
        super(
                "NOTE_GENERATION_LIMIT_REACHED",
                "You have reached your note generation limit for this billing cycle.",
                HttpStatus.FORBIDDEN
        );
    }
}
