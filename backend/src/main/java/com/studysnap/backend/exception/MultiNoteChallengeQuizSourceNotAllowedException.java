package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

/** Rejects, rather than silently dropping, source selections the caller's plan cannot use. */
public class MultiNoteChallengeQuizSourceNotAllowedException extends AppException {
    public MultiNoteChallengeQuizSourceNotAllowedException() {
        super(
                "MULTI_NOTE_CHALLENGE_QUIZ_SOURCE_NOT_ALLOWED",
                "Too many notes selected for your Challenge Quiz source limit.",
                HttpStatus.BAD_REQUEST
        );
    }
}
