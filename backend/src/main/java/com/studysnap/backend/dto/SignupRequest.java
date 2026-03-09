package com.studysnap.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Please provide a valid email address.")
        String email,
        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters.")
        String password,
        @NotBlank(message = "First name is required.")
        @Size(max = 100, message = "First name is too long.")
        String firstName,
        @Size(max = 100, message = "Display name is too long.")
        String displayName
) {
}
