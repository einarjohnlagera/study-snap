package com.studysnap.backend.service.model;

import com.studysnap.backend.dto.QuizItem;

import java.util.List;

public record GeneratedChallengeQuizContent(
        List<QuizItem> quizItems,
        String modelUsed,
        Integer inputTokens,
        Integer outputTokens,
        Integer cachedInputTokens
) {
    public static GeneratedChallengeQuizContent withoutUsage(List<QuizItem> quizItems) {
        return new GeneratedChallengeQuizContent(quizItems, null, null, null, null);
    }
}
