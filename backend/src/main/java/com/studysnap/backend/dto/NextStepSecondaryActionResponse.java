package com.studysnap.backend.dto;

public record NextStepSecondaryActionResponse(
        String actionLabel,
        String actionHref,
        boolean adaptivePractice,
        boolean studyPlanRecommendation,
        String courseProgram,
        String recommendedPlanId
) {
    public NextStepSecondaryActionResponse(String actionLabel, String actionHref, boolean adaptivePractice) {
        this(actionLabel, actionHref, adaptivePractice, false, null, null);
    }
}
