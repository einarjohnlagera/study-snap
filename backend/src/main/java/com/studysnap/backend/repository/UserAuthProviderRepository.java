package com.studysnap.backend.repository;

import com.studysnap.backend.entity.AuthProvider;
import com.studysnap.backend.entity.UserAuthProviderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAuthProviderRepository extends JpaRepository<UserAuthProviderEntity, UUID> {
    Optional<UserAuthProviderEntity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    Optional<UserAuthProviderEntity> findByUserIdAndProvider(UUID userId, AuthProvider provider);

    void deleteByUserId(UUID userId);
}
