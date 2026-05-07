package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleAuthRequest(
        @NotBlank(message = "Google authorization code is required.")
        String code,
        Boolean keepSignedIn
) {
}
