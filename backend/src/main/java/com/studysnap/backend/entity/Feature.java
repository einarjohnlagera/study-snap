package com.studysnap.backend.entity;

import lombok.Getter;

@Getter
public enum Feature {
    ADAPTIVE_QUIZ(
            "Adaptive Practice is a Premium feature. Upgrade to Premium to continue."
    ),
    DIFFICULTY_SELECTION(
            "Difficulty selection is a Premium feature. Upgrade to Premium to continue."
    ),
    WEAK_CONCEPT_DETECTION(
            "Weak concepts are available."
    );

    private final String accessDeniedMessage;

    Feature(String accessDeniedMessage) {
        this.accessDeniedMessage = accessDeniedMessage;
    }

}
