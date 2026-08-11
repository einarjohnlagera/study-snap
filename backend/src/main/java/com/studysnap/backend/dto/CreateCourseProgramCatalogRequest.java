package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCourseProgramCatalogRequest(
        @NotBlank(message = "Course / Program name is required.")
        @Size(max = 120, message = "Course / Program name must be 120 characters or fewer.")
        String name,
        UUID programFamilyId,
        String examGoalSlug
) {
}
