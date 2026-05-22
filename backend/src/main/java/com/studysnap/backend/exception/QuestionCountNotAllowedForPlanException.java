package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class QuestionCountNotAllowedForPlanException extends AppException {
    private static final String ERROR_CODE = "QUESTION_COUNT_NOT_ALLOWED";
    private static final String MESSAGE = "Plus unlocks 20- and 30-question quizzes.";
    private static final String DETAILS = "questionCount=teacher-plus";
    private static final String ACTION = "UPGRADE_TO_PLUS";

    public QuestionCountNotAllowedForPlanException() {
        super(ERROR_CODE, MESSAGE, DETAILS, ACTION, HttpStatus.PAYMENT_REQUIRED);
    }
}
