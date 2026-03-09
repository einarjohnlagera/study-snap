package com.studysnap.backend.dto;

import com.studysnap.backend.entity.ProfileType;
import jakarta.validation.constraints.NotNull;

public record OnboardingProfileTypeRequest(
        @NotNull(message = "Profile type is required.")
        ProfileType profileType
) {
}
