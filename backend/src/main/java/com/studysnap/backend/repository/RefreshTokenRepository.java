package com.studysnap.backend.repository;

import com.studysnap.backend.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
    void deleteByUserId(UUID userId);
    void deleteByExpiresAtBefore(OffsetDateTime threshold);
}
