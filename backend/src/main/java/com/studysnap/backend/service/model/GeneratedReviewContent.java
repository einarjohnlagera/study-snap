package com.studysnap.backend.service.model;

import com.studysnap.backend.dto.QuizItem;

import java.math.BigDecimal;
import java.util.List;

public record GeneratedReviewContent(
        String title,
        String summary,
        List<String> keyConcepts,
        List<QuizItem> quiz,
        String modelUsed,
        Integer inputTokens,
        Integer outputTokens,
        Integer cachedInputTokens,
        BigDecimal estimatedCost
) {
}
