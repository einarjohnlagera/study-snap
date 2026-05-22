package com.studysnap.backend.dto;

import com.studysnap.backend.entity.LearnerLevel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @NotBlank(message = "First name is required.")
        @Size(max = 100, message = "First name must be 100 characters or less.")
        String firstName,

        @Size(max = 100, message = "Last name must be 100 characters or less.")
        String lastName,

        @Size(max = 100, message = "Display name must be 100 characters or less.")
        String displayName,

        @Size(max = 30, message = "Username must be 3-30 characters.")
        String username,

        @Size(max = 200, message = "Bio must be 200 characters or less.")
        String bio,

        LearnerLevel learnerLevel,

        @Size(max = 120, message = "Course / Program must be 120 characters or less.")
        String courseProgram,

        @Size(max = 120, message = "School name must be 120 characters or less.")
        String schoolName,

        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 255, message = "Email must be 255 characters or less.")
        String email
) {
}
