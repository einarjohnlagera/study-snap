package com.studysnap.backend.dto;

import java.util.List;
import java.util.Map;

public record ChallengeQuizStartResponse(
        String sessionId,
        String studyPackId,
        String title,
        int totalQuestions,
        int timeLimitSeconds,
        int usedThisMonth,
        int monthlyLimit,
        List<QuizItem> quiz,
        int currentQuestionIndex,
        Map<String, Object> sessionState
) {
}
