package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a caller who may not choose a question count sends one anyway.
 *
 * <p>⚠️ This is a CAPABILITY boundary, not a paywall, and it must stay distinct from
 * {@link QuestionCountNotAllowedForPlanException}. That one carries {@code UPGRADE_TO_PLUS} and
 * {@code PAYMENT_REQUIRED} — correct for a Teacher on Free, who really can buy the capability. Reusing it
 * here would sell Plus for something Plus does not grant, because question count is gated on the Teacher
 * profile and no plan changes that.
 */
public class QuestionCountNotSelectableException extends AppException {
    public QuestionCountNotSelectableException() {
        super(
                "QUESTION_COUNT_NOT_SELECTABLE",
                "Choosing the number of questions is available for Teacher accounts only.",
                HttpStatus.FORBIDDEN
        );
    }
}
