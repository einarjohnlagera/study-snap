package com.studysnap.backend.dto;

import com.studysnap.backend.entity.QuickReviewRound;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record QuickReviewSessionProgressRequest(
        @NotNull @Min(0) Integer currentQuestionIndex,
        @NotNull QuickReviewRound currentRound,
        @NotNull @Min(0) Integer retryCount,
        Map<String, Object> sessionState
) {
}
