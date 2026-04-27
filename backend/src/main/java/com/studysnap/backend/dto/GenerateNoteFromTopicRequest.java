package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateNoteFromTopicRequest(
        @NotBlank(message = "Topic is required.")
        @Size(max = 160, message = "Topic must be 160 characters or less.")
        String topic
) {
}
