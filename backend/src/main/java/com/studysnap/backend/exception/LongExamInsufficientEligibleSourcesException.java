package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class LongExamInsufficientEligibleSourcesException extends AppException {
    public LongExamInsufficientEligibleSourcesException(int eligibleSourceCount, int requiredSourceCount) {
        super(
                "LONG_EXAM_INSUFFICIENT_ELIGIBLE_SOURCES",
                "This Study Plan has " + eligibleSourceCount + " ready Study Pack"
                        + (eligibleSourceCount == 1 ? "" : "s") + "; at least " + requiredSourceCount
                        + " are needed to build a representative Long Exam.",
                HttpStatus.BAD_REQUEST
        );
    }
}
