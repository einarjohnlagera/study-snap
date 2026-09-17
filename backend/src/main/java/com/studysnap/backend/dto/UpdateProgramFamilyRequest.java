package com.studysnap.backend.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdateProgramFamilyRequest(
        @Size(max = 120, message = "Program Family name must be 120 characters or fewer.")
        String name,
        List<UUID> programIds
) {
}
