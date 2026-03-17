package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateStudyPackMetadataRequest(
        @NotBlank(message = "Title is required.")
        String title,
        String subject
) {
}
