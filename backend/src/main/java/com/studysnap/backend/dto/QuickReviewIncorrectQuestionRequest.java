package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record QuickReviewIncorrectQuestionRequest(
        @NotBlank String question,
        @NotBlank String correctAnswer,
        @NotBlank String explanation
) {
}
