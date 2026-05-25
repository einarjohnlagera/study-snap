package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record CopyOnSignupRequest(
        @NotBlank(message = "publicNoteId is required")
        String publicNoteId
) {
}
