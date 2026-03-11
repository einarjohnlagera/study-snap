package com.studysnap.backend.repository;

import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
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

    List<QuickReviewSessionEntity> findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID userId,
            Pageable pageable
    );

    Optional<QuickReviewSessionEntity> findTopByUserIdAndStudyPackIdAndStatusOrderByCreatedAtDesc(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionStatus status
    );

    Optional<QuickReviewSessionEntity> findTopByUserIdAndStatusOrderByCreatedAtDesc(
            UUID userId,
            QuickReviewSessionStatus status
    );

    long countByUserIdAndStudyPackIdAndCompletedAtIsNotNull(UUID userId, UUID studyPackId);

    @Query("""
            select max(q.scorePercentage)
            from QuickReviewSessionEntity q
            where q.userId = :userId
              and q.studyPackId = :studyPackId
              and q.completedAt is not null
            """)
    java.math.BigDecimal findBestScorePercentageByUserIdAndStudyPackId(UUID userId, UUID studyPackId);
}
