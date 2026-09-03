package com.studysnap.backend.dto;

public enum ContinueStudyingResumeType {
    QUICK_REVIEW,
    CHALLENGE,
    ADAPTIVE,
    /**
     * Interview Practice.
     *
     * <p>It shares the {@code ADAPTIVE} session discriminator, so a resume type derived from the
     * session MODE alone cannot distinguish it — which is how an in-progress Interview Practice
     * session came to be offered as an Adaptive Practice card that routes somewhere refusing it.
     * The distinguishing fact is the {@code subMode} string in session state, not the mode.
     */
    INTERVIEW_PRACTICE
}
