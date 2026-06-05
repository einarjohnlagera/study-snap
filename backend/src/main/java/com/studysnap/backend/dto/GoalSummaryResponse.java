package com.studysnap.backend.dto;

public record GoalSummaryResponse(
        String examGoal,
        String examShortName,
        String examFullName,
        int masteryPercentage,
        int masteredConcepts,
        int totalConcepts,
        String weakestGoalSubject
) {
}
