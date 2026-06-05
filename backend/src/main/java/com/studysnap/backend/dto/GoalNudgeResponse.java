package com.studysnap.backend.dto;

public record GoalNudgeResponse(
        String studyGoal,
        String goalType,
        String goalName,
        String goalLabel,
        int masteryPercentage,
        int dueConcepts
) {
}
