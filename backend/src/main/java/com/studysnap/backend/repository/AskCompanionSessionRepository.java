package com.studysnap.backend.repository;

import com.studysnap.backend.entity.AskCompanionSessionEntity;
import com.studysnap.backend.entity.AskCompanionSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AskCompanionSessionRepository extends JpaRepository<AskCompanionSessionEntity, UUID> {
    Optional<AskCompanionSessionEntity> findTopByUserIdAndCollectionIdAndStatusOrderByCreatedAtDesc(
            UUID userId,
            UUID collectionId,
            AskCompanionSessionStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from AskCompanionSessionEntity session
            where session.id = :sessionId
              and session.userId = :userId
            """)
    Optional<AskCompanionSessionEntity> findByIdAndUserIdForUpdate(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId
    );
}
