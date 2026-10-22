package com.studysnap.backend.dto;

import java.util.List;

public record GenerateMoreChallengeQuizResponse(
        String sessionId,
        List<QuizItem> newQuestions,
        int totalQuestions,
        int timeLimitSeconds,
        long timerStartedAtEpochSeconds
) {
    @com.fasterxml.jackson.annotation.JsonProperty("morePending")
    public boolean morePending() { return true; }
}
