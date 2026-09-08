package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProgramFamilyRequest(
        @NotBlank(message = "Program Family name is required.")
        @Size(max = 120, message = "Program Family name must be 120 characters or fewer.")
        String name
) {
}
