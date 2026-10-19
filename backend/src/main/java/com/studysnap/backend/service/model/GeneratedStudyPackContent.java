package com.studysnap.backend.service.model;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.ModelTier;

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
        BigDecimal estimatedCost,
        ModelTier resolvedModelTier,
        String modelSent
) {
    public GeneratedStudyPackContent(String title, String summary, String subject, List<String> tags,
            List<String> keyConcepts, List<QuizItem> quiz, String modelUsed, Integer inputTokens,
            Integer outputTokens, Integer cachedInputTokens, BigDecimal estimatedCost) {
        this(title, summary, subject, tags, keyConcepts, quiz, modelUsed, inputTokens,
                outputTokens, cachedInputTokens, estimatedCost, null, null);
    }

    public GeneratedStudyPackContent(String title, String summary, String subject, List<String> tags,
            List<String> keyConcepts, List<QuizItem> quiz, String modelUsed, Integer inputTokens,
            Integer outputTokens, Integer cachedInputTokens, BigDecimal estimatedCost, ModelTier resolvedModelTier) {
        this(title, summary, subject, tags, keyConcepts, quiz, modelUsed, inputTokens,
                outputTokens, cachedInputTokens, estimatedCost, resolvedModelTier, null);
    }
}
