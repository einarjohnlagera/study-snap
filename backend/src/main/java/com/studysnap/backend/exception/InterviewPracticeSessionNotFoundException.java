package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InterviewPracticeSessionNotFoundException extends AppException {
    public InterviewPracticeSessionNotFoundException() {
        super(
                "INTERVIEW_PRACTICE_SESSION_NOT_FOUND",
                "Interview Practice session not found.",
                HttpStatus.NOT_FOUND
        );
    }
}
