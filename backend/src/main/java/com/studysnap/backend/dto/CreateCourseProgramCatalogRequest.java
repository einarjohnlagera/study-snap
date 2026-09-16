package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;
import java.util.List;

public record CreateCourseProgramCatalogRequest(
        @NotBlank(message = "Course / Program name is required.")
        @Size(max = 120, message = "Course / Program name must be 120 characters or fewer.")
        String name,
        UUID programFamilyId,
        List<UUID> programFamilyIds,
        String examGoalSlug
) {
    public CreateCourseProgramCatalogRequest(String name, UUID programFamilyId, String examGoalSlug) {
        this(name, programFamilyId, null, examGoalSlug);
    }

    public List<UUID> effectiveProgramFamilyIds() {
        if (programFamilyIds != null) return programFamilyIds;
        return programFamilyId == null ? List.of() : List.of(programFamilyId);
    }
}
