package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateProgramFamilyRequest(
        @NotBlank(message = "Program Family name is required.")
        @Size(max = 120, message = "Program Family name must be 120 characters or fewer.")
        String name,
        List<UUID> programIds
) {
    public CreateProgramFamilyRequest {
        programIds = programIds == null ? List.of() : List.copyOf(programIds);
    }

    public CreateProgramFamilyRequest(String name) {
        this(name, List.of());
    }
}
