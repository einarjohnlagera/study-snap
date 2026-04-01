package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotNull;

public record UpdatePublicProfileVisibilityRequest(
        @NotNull Boolean publicProfileVisible
) {
}
