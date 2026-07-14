package com.studysnap.backend.dto;

public record DashboardOverviewResponse(
        DashboardPerformanceSummaryResponse performanceSummary,
        DashboardFocusAreasResponse focusAreas,
        DashboardWeeklyActivityResponse weeklyActivity,
        ExamPacingPlanResponse examPacingPlan
) {
}
