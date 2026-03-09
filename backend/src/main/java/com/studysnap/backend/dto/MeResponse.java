package com.studysnap.backend.dto;

import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
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
        OffsetDateTime emailVerifiedAt,
        UserStatus status,
        PlanType planType
) {
}
