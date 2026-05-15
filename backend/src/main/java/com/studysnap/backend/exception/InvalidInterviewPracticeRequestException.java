package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidInterviewPracticeRequestException extends AppException {
    public InvalidInterviewPracticeRequestException(String message) {
        super(
                "INVALID_INTERVIEW_PRACTICE_REQUEST",
                message,
                HttpStatus.BAD_REQUEST
        );
    }
}
