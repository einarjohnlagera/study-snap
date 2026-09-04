package com.studysnap.backend.repository;

import com.studysnap.backend.entity.CombinedQuizEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CombinedQuizRepository extends JpaRepository<CombinedQuizEntity, UUID> {
    Optional<CombinedQuizEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    List<CombinedQuizEntity> findByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId, Pageable pageable);
}
