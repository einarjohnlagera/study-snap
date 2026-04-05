package com.studysnap.backend.dto;

import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.ProfileType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CompleteOnboardingRequest(
        @NotNull(message = "Profile type is required.")
        ProfileType profileType,
        @NotNull(message = "Please select your learner level.")
        LearnerLevel learnerLevel,
        @NotBlank(message = "Please select or enter your course / program.")
        @Size(max = 120, message = "Course / Program must be 120 characters or less.")
        String courseProgram,
        @Size(max = 200, message = "Bio must be 200 characters or less.")
        String bio,
        @NotNull(message = "Learning style is required.")
        EngagementMode engagementMode,
        @NotNull(message = "Inactivity reminders preference is required.")
        Boolean inactivityRemindersEnabled,
        @NotNull(message = "Weak concept reminders preference is required.")
        Boolean weakConceptRemindersEnabled,
        LocalDate examDate
) {
}
