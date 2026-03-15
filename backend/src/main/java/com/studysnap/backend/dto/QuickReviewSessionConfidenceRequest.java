package com.studysnap.backend.dto;

import com.studysnap.backend.entity.QuickReviewConfidenceLevel;
import jakarta.validation.constraints.NotNull;

public record QuickReviewSessionConfidenceRequest(
        @NotNull QuickReviewConfidenceLevel confidenceLevel
) {
}
