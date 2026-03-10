package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record QuickReviewSessionStartRequest(
        @NotBlank String studyPackId
) {
}
