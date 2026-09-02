package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * The exam's own primary Note has no ready Study Pack, so it cannot anchor a plan-sourced exam.
 *
 * <p>Reachable because the start-time primary lookup does not filter on {@code StudyPackStatus}, while the
 * eligible pool does. Named so the learner gets an error code and a message rather than a generic 500.
 */
public class LongExamPrimarySourceNotEligibleException extends AppException {
    public LongExamPrimarySourceNotEligibleException() {
        super(
                "LONG_EXAM_PRIMARY_SOURCE_NOT_ELIGIBLE",
                "This Note needs a ready Study Pack before it can anchor a plan exam.",
                HttpStatus.CONFLICT
        );
    }
}
