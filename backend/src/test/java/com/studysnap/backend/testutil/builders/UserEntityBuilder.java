package com.studysnap.backend.testutil.builders;

import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.With;

import java.time.OffsetDateTime;
import java.util.UUID;

@With
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserEntityBuilder {
    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String firstName;
    private final String lastName;
    private final String displayName;
    private final String countryCode;
    private final UserStatus status;
    private final UserRole role;
    private final Integer tokenVersion;
    private final Integer failedLoginAttempts;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public static UserEntityBuilder aUser() {
        OffsetDateTime now = OffsetDateTime.now().minusDays(1);
        return new UserEntityBuilder(
                UUID.randomUUID(),
                "student+" + UUID.randomUUID() + "@example.com",
                "hashed-password",
                "Study",
                "User",
                "Study User",
                "PH",
                UserStatus.ACTIVE,
                UserRole.USER,
                0,
                0,
                now,
                now
        );
    }

    public UserEntity build() {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setDisplayName(displayName);
        user.setCountryCode(countryCode);
        user.setStatus(status);
        user.setRole(role);
        user.setTokenVersion(tokenVersion);
        user.setFailedLoginAttempts(failedLoginAttempts);
        user.setCreatedAt(createdAt);
        user.setUpdatedAt(updatedAt);
        return user;
    }
}
