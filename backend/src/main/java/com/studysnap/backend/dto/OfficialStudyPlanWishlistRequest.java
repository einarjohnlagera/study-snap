package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OfficialStudyPlanWishlistRequest(
        @NotBlank(message = "Course / Program is required.")
        @Size(max = 120, message = "Course / Program must be 120 characters or fewer.")
        String courseProgram
) {
}
