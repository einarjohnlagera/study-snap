package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReplaceApplicableProgramsRequest(
        @NotNull List<@NotNull UUID> courseProgramIds
) {
}
