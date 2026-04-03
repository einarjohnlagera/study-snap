package com.studysnap.backend.dto;

import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.ThemePreference;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record MeResponse(
        String id,
        String email,
        String pendingEmail,
        String firstName,
        String lastName,
        String displayName,
        String bio,
        boolean publicProfileVisible,
        String countryCode,
        ProfileType profileType,
        LocalDate examDate,
        EngagementMode engagementMode,
        boolean inactivityRemindersEnabled,
        boolean weakConceptRemindersEnabled,
        ThemePreference themePreference,
        OffsetDateTime emailVerifiedAt,
        OffsetDateTime onboardingCompletedAt,
        OffsetDateTime productOnboardingCompletedAt,
        long studyPackCount,
        UserRole role,
        UserStatus status,
        PlanType planType,
        SubscriptionPlanStatusResponse subscription
) {
}
