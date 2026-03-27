package com.studysnap.backend.dto;

import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.ProfileType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CompleteOnboardingRequest(
        @NotNull(message = "Profile type is required.")
        ProfileType profileType,
        @NotNull(message = "Learning style is required.")
        EngagementMode engagementMode,
        @NotNull(message = "Inactivity reminders preference is required.")
        Boolean inactivityRemindersEnabled,
        @NotNull(message = "Weak concept reminders preference is required.")
        Boolean weakConceptRemindersEnabled,
        LocalDate examDate
) {
}
