package com.studysnap.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @NotBlank(message = "First name is required.")
        @Size(max = 100, message = "First name must be 100 characters or less.")
        String firstName,

        @Size(max = 100, message = "Last name must be 100 characters or less.")
        String lastName,

        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 255, message = "Email must be 255 characters or less.")
        String email
) {
}
