package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

/** A matching block cannot represent two source packs without corrupting provenance. */
public class MatchingQuestionGroupSourceMismatchException extends AppException {
    public MatchingQuestionGroupSourceMismatchException() {
        super(
                "MATCHING_QUESTION_GROUP_SOURCE_MISMATCH",
                "A matching question group spans more than one source note.",
                HttpStatus.BAD_GATEWAY
        );
    }
}
