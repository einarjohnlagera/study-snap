package com.studysnap.backend.dto;

import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;

import java.time.OffsetDateTime;

public record AuthResponse(
        String userId,
        String email,
        String displayName,
        ProfileType profileType,
        OffsetDateTime emailVerifiedAt,
        PlanType planType,
        String token
) {
}
