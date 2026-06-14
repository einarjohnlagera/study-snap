package com.studysnap.backend.dto;

import java.util.List;

public record NextStepResponse(
        TodayFocusType type,
        String studyPackId,
        String noteId,
        String title,
        String message,
        String actionLabel,
        String actionHref,
        List<String> concepts,
        boolean adaptivePracticeAvailable,
        Integer adaptivePracticeRemaining,
        GoalNudgeResponse goalNudge,
        NextStepSecondaryActionResponse secondaryAction
) {
}
