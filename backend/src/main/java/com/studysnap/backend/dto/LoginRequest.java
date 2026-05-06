package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email or username is required.")
        String email,
        @NotBlank(message = "Password is required.")
        String password,
        Boolean keepSignedIn
) {
}
