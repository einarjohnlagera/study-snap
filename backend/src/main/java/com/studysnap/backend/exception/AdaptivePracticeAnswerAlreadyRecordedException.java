package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class AdaptivePracticeAnswerAlreadyRecordedException extends AppException {
    public AdaptivePracticeAnswerAlreadyRecordedException() {
        super(
                "ADAPTIVE_PRACTICE_ANSWER_ALREADY_RECORDED",
                "This Adaptive Practice question has already been answered.",
                HttpStatus.CONFLICT
        );
    }
}
