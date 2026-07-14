package com.studysnap.backend.dto;

public record ExamPacingPlanResponse(
        int dueConceptCount,
        int dailyConceptTarget,
        int daysRemaining
) {
}
