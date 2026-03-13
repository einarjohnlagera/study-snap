package com.studysnap.backend.dto;

public record TodayFocusResponse(
        TodayFocusType type,
        String studyPackId,
        String title,
        String message,
        String actionLabel
) {
}
