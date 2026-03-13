package com.studysnap.backend.service.model;

import com.studysnap.backend.dto.QuizItem;

import java.math.BigDecimal;
import java.util.List;

public record GeneratedStudyPackContent(
        String title,
        String summary,
        String subject,
        List<String> tags,
        List<String> keyConcepts,
        List<QuizItem> quiz,
        String modelUsed,
        Integer inputTokens,
        Integer outputTokens,
        Integer cachedInputTokens,
        BigDecimal estimatedCost
) {
}

