package com.studysnap.backend.dto;

public record GoalSummaryResponse(
        String studyGoal,
        String goalType,
        String goalName,
        String goalLabel,
        int masteryPercentage,
        int masteredConcepts,
        int totalConcepts,
        String weakestGoalSubject
) {
}
