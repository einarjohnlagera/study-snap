package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskCompanionQuestionRequest(
        @NotBlank(message = "Question is required.")
        @Size(max = 1000, message = "Question must be 1000 characters or fewer.")
        String question
) {
}
