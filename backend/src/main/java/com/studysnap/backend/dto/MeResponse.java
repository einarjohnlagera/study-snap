package com.studysnap.backend.dto;

import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;

import java.time.OffsetDateTime;

public record MeResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String displayName,
        String countryCode,
        ProfileType profileType,
        EngagementMode engagementMode,
        OffsetDateTime emailVerifiedAt,
        UserRole role,
        UserStatus status,
        PlanType planType
) {
}
