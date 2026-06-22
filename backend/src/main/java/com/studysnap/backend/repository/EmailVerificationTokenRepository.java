package com.studysnap.backend.repository;

import com.studysnap.backend.entity.EmailVerificationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationTokenEntity, UUID> {
    Optional<EmailVerificationTokenEntity> findByTokenHash(String tokenHash);

    Optional<EmailVerificationTokenEntity> findTopByUserIdOrderByCreatedAtDesc(UUID userId);

    List<EmailVerificationTokenEntity> findByUserIdAndUsedAtIsNull(UUID userId);

    void deleteByUserId(UUID userId);
}
