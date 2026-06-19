package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCollectionVisibilityRequest(
        @NotBlank(message = "visibility is required")
        String visibility
) {
}
