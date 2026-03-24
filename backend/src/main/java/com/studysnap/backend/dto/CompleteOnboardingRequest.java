package com.studysnap.backend.dto;

import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.ProfileType;
import jakarta.validation.constraints.NotNull;

public record CompleteOnboardingRequest(
        @NotNull(message = "Profile type is required.")
        ProfileType profileType,
        @NotNull(message = "Learning style is required.")
        EngagementMode engagementMode
) {
}
