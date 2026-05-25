package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateQuizShareLinkRequest(
        @NotNull UUID generatedQuizId
) {
}
