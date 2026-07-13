package com.studysnap.backend.dto;

import java.util.List;

public record TodayFocusResponse(
        TodayFocusType type,
        String studyPackId,
        String noteId,
        String title,
        String message,
        String actionLabel,
        List<TodayFocusConceptResponse> concepts,
        boolean adaptivePracticeAvailable
) {
}
