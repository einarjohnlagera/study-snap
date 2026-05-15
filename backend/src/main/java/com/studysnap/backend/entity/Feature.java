package com.studysnap.backend.entity;

import lombok.Getter;

@Getter
public enum Feature {
    ADAPTIVE_QUIZ(
            "Adaptive Practice is a Pro feature. Upgrade to Pro to continue."
    ),
    DIFFICULTY_SELECTION(
            "Difficulty selection is a Pro feature. Upgrade to Pro to continue."
    ),
    LONG_EXAM_SESSION(
            "Long Exam Mode requires a Pro plan."
    ),
    INTERVIEW_PRACTICE(
            "Interview Practice is a Pro feature. Upgrade to Pro to continue."
    ),
    WEAK_CONCEPT_DETECTION(
            "Weak concepts are available."
    );

    private final String accessDeniedMessage;

    Feature(String accessDeniedMessage) {
        this.accessDeniedMessage = accessDeniedMessage;
    }

}
