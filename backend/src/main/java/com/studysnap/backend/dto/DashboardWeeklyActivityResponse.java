package com.studysnap.backend.dto;

public record DashboardWeeklyActivityResponse(
        int studyPacksCreated,
        int quizzesTaken,
        int adaptiveSessions,
        int studyDays
) {
}
