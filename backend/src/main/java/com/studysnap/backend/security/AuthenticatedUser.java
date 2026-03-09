package com.studysnap.backend.security;

import com.studysnap.backend.entity.UserRole;

import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        UserRole role,
        boolean emailVerified,
        int tokenVersion
) {
}
