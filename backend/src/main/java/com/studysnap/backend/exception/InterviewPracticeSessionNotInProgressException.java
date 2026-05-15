package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InterviewPracticeSessionNotInProgressException extends AppException {
    public InterviewPracticeSessionNotInProgressException() {
        super(
                "INTERVIEW_PRACTICE_SESSION_NOT_IN_PROGRESS",
                "Interview Practice session is not in progress.",
                HttpStatus.CONFLICT
        );
    }
}
