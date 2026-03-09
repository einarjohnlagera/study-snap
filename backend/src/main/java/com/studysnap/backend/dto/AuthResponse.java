package com.studysnap.backend.dto;

import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;

public record AuthResponse(
        String userId,
        String email,
        String displayName,
        ProfileType profileType,
        PlanType planType,
        String token
) {
}
