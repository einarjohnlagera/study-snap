package com.studysnap.backend.dto;

public record FeedbackPromptContextResponse(
        boolean returningAfterInactivity,
        boolean hasCompletedQuizSession
) {
}
