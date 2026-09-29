package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class QuickReviewAnswerAlreadyRecordedException extends AppException {
    public QuickReviewAnswerAlreadyRecordedException() {
        super(
                "QUICK_REVIEW_ANSWER_ALREADY_RECORDED",
                "This Quick Review question has already been answered in this attempt.",
                HttpStatus.CONFLICT
        );
    }
}
