package com.studysnap.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdaptivePracticeCompleteRequest(
        @NotNull @Min(0) Integer correctAnswers,
        @NotNull @Min(1) Integer totalQuestions,
        @Min(0) Integer durationSeconds
) {
}
