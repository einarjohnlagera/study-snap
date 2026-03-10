package com.studysnap.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record QuickReviewSessionCompleteRequest(
        @NotNull @Min(0) Integer correctAnswers,
        @NotNull @Min(1) Integer totalQuestions,
        @NotNull @Min(0) Integer retryCount,
        @Min(0) Integer durationSeconds,
        Map<String, Object> sessionMetadata
) {
}
