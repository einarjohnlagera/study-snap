package com.studysnap.backend.repository;

import com.studysnap.backend.entity.QuickReviewSessionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuickReviewSessionRepository extends JpaRepository<QuickReviewSessionEntity, UUID> {
    Optional<QuickReviewSessionEntity> findByIdAndUserId(UUID id, UUID userId);

    List<QuickReviewSessionEntity> findByUserIdAndStudyPackIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID userId,
            UUID studyPackId,
            Pageable pageable
    );
}
