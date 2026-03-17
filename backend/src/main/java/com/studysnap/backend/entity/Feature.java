package com.studysnap.backend.entity;

import lombok.Getter;

@Getter
public enum Feature {
    CHALLENGE_QUIZ(
            PlanType.PREMIUM,
            "Challenge Quiz is available in the Premium plan."
    ),
    ADAPTIVE_QUIZ(
            PlanType.PREMIUM,
            "Adaptive Quiz Generation is a Premium feature. Upgrade to Premium to continue."
    ),
    WEAK_CONCEPT_DETECTION(
            PlanType.PREMIUM,
            "Weak Concept Detection is a Premium feature. Upgrade to Premium to continue."
    );

    private final PlanType requiredPlan;
    private final String accessDeniedMessage;

    Feature(PlanType requiredPlan, String accessDeniedMessage) {
        this.requiredPlan = requiredPlan;
        this.accessDeniedMessage = accessDeniedMessage;
    }

}
