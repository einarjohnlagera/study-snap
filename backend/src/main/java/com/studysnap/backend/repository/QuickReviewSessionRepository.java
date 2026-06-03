package com.studysnap.backend.repository;

import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuickReviewSessionRepository extends JpaRepository<QuickReviewSessionEntity, UUID> {
    Optional<QuickReviewSessionEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<QuickReviewSessionEntity> findByIdAndUserIdAndSessionMode(UUID id, UUID userId, QuickReviewSessionMode sessionMode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select q
            from QuickReviewSessionEntity q
            where q.id = :id
              and q.userId = :userId
              and q.sessionMode = :sessionMode
            """)
    Optional<QuickReviewSessionEntity> findByIdAndUserIdAndSessionModeForUpdate(UUID id, UUID userId, QuickReviewSessionMode sessionMode);

    @Deprecated
    List<QuickReviewSessionEntity> findByUserIdAndStudyPackIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID userId,
            UUID studyPackId,
            Pageable pageable
    );

    List<QuickReviewSessionEntity> findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionMode sessionMode,
            Pageable pageable
    );

    List<QuickReviewSessionEntity> findByUserIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID userId,
            QuickReviewSessionMode sessionMode,
            Pageable pageable
    );

    List<QuickReviewSessionEntity> findByUserIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID userId,
            QuickReviewSessionMode sessionMode
    );

    List<QuickReviewSessionEntity> findByUserIdAndSessionModeInAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID userId,
            Collection<QuickReviewSessionMode> sessionModes
    );

    List<QuickReviewSessionEntity> findByUserIdAndStatusAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID userId,
            QuickReviewSessionStatus status
    );

    @Deprecated
    List<QuickReviewSessionEntity> findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID userId,
            Pageable pageable
    );

    Optional<QuickReviewSessionEntity> findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionMode sessionMode,
            QuickReviewSessionStatus status
    );

    boolean existsByStudyPackIdAndSessionModeAndStatus(
            UUID studyPackId,
            QuickReviewSessionMode sessionMode,
            QuickReviewSessionStatus status
    );

    Optional<QuickReviewSessionEntity> findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionMode sessionMode,
            Collection<QuickReviewSessionStatus> statuses
    );

    @Deprecated
    Optional<QuickReviewSessionEntity> findTopByUserIdAndStudyPackIdAndStatusOrderByCreatedAtDesc(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionStatus status
    );

    Optional<QuickReviewSessionEntity> findTopByUserIdAndSessionModeAndStatusOrderByCreatedAtDesc(
            UUID userId,
            QuickReviewSessionMode sessionMode,
            QuickReviewSessionStatus status
    );

    @Deprecated
    Optional<QuickReviewSessionEntity> findTopByUserIdAndStatusOrderByCreatedAtDesc(
            UUID userId,
            QuickReviewSessionStatus status
    );

    long countByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNull(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionMode sessionMode
    );

    @Deprecated
    long countByUserIdAndStudyPackIdAndCompletedAtIsNotNull(UUID userId, UUID studyPackId);

    long countByUserIdAndSessionModeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID userId,
            QuickReviewSessionMode sessionMode,
            OffsetDateTime createdAtStart,
            OffsetDateTime createdAtEnd
    );

    long countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID userId,
            QuickReviewSessionMode sessionMode,
            Collection<QuickReviewSessionStatus> statuses,
            OffsetDateTime createdAtStart,
            OffsetDateTime createdAtEnd
    );

    @Query("""
            select max(q.scorePercentage)
            from QuickReviewSessionEntity q
            where q.userId = :userId
              and q.studyPackId = :studyPackId
              and q.sessionMode = :sessionMode
              and q.completedAt is not null
            """)
    java.math.BigDecimal findBestScorePercentageByUserIdAndStudyPackIdAndSessionMode(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionMode sessionMode
    );

    @Deprecated
    @Query("""
            select max(q.scorePercentage)
            from QuickReviewSessionEntity q
            where q.userId = :userId
              and q.studyPackId = :studyPackId
              and q.completedAt is not null
            """)
    java.math.BigDecimal findBestScorePercentageByUserIdAndStudyPackId(UUID userId, UUID studyPackId);

}
