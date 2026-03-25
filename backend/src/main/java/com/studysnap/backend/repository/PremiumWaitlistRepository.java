package com.studysnap.backend.repository;

import com.studysnap.backend.entity.PremiumWaitlistEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PremiumWaitlistRepository extends JpaRepository<PremiumWaitlistEntity, UUID> {
    boolean existsByUserId(UUID userId);
}
