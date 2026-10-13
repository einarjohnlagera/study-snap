package com.studysnap.backend.dto;

import java.util.List;

public record GoalSummaryResponse(
        String studyGoal,
        String goalType,
        String goalName,
        String goalLabel,
        int masteryPercentage,
        int masteredConcepts,
        int totalConcepts,
        int notPracticedConcepts,
        String weakestGoalSubject,
        List<ExamGoalResponse> examGoals
) {
    public GoalSummaryResponse(String studyGoal, String goalType, String goalName, String goalLabel,
            int masteryPercentage, int masteredConcepts, int totalConcepts,
            int notPracticedConcepts, String weakestGoalSubject) {
        this(studyGoal, goalType, goalName, goalLabel, masteryPercentage, masteredConcepts,
                totalConcepts, notPracticedConcepts, weakestGoalSubject, List.of());
    }
}
