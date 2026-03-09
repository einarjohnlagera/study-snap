package com.studysnap.backend.dto;

import com.studysnap.backend.entity.ProfileType;
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
        @NotBlank(message = "Last name is required.")
        @Size(max = 100, message = "Last name is too long.")
        String lastName,
        @Size(max = 100, message = "Display name is too long.")
        String displayName,
        @Size(max = 8, message = "Country code is too long.")
        String countryCode,
        ProfileType profileType
) {
}
