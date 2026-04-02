package com.studysnap.backend.dto;

import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.ThemePreference;
import com.studysnap.backend.entity.UserRole;

import java.time.OffsetDateTime;

public record AuthResponse(
        String userId,
        String email,
        String displayName,
        ProfileType profileType,
        OffsetDateTime emailVerifiedAt,
        OffsetDateTime onboardingCompletedAt,
        OffsetDateTime productOnboardingCompletedAt,
        ThemePreference themePreference,
        UserRole role,
        PlanType planType,
        String token,
        String refreshToken,
        OffsetDateTime accessTokenExpiresAt,
        OffsetDateTime refreshTokenExpiresAt
) {
}
