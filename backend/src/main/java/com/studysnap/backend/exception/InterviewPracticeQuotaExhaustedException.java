package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InterviewPracticeQuotaExhaustedException extends AppException {
    public InterviewPracticeQuotaExhaustedException() {
        super(
                "MONTHLY_INTERVIEW_PRACTICE_LIMIT_REACHED",
                "You've reached your monthly Interview Practice limit.",
                HttpStatus.FORBIDDEN
        );
    }
}
