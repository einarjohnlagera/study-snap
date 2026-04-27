package com.studysnap.backend.dto;

import com.studysnap.backend.entity.ProfileType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CompleteOnboardingRequest(
        @NotNull(message = "Profile type is required.")
        ProfileType profileType,
        LocalDate examDate
) {
}
