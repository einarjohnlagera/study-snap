package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class InterviewPracticeAnswerAlreadyRecordedException extends AppException {
    public InterviewPracticeAnswerAlreadyRecordedException() {
        super(
                "INTERVIEW_PRACTICE_ANSWER_ALREADY_RECORDED",
                "This Interview Practice question has already been answered.",
                HttpStatus.CONFLICT
        );
    }
}
