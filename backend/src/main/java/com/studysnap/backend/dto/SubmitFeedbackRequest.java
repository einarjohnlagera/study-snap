package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitFeedbackRequest(
        @NotBlank(message = "Message is required.")
        @Size(max = 4000, message = "Feedback message is too long.")
        String message
) {
}
