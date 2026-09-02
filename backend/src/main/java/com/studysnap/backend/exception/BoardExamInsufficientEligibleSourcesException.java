package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

/** A Review Set cannot form a representative Board Exam because too few members have ready Study Packs. */
public class BoardExamInsufficientEligibleSourcesException extends AppException {
    public BoardExamInsufficientEligibleSourcesException(int eligibleSources, int requiredSources) {
        super(
                "BOARD_EXAM_INSUFFICIENT_ELIGIBLE_SOURCES",
                "This Review Set has " + eligibleSources + " ready Study Pack" + (eligibleSources == 1 ? "" : "s")
                        + "; at least " + requiredSources + " are needed to build a representative Board Exam.",
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }
}
