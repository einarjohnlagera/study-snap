package com.studysnap.backend.repository;

import com.studysnap.backend.entity.StudyPackEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyPackRepository extends JpaRepository<StudyPackEntity, UUID> {
    List<StudyPackEntity> findByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);
    List<StudyPackEntity> findByOwnerUserIdOrderByCreatedAtDescIdDesc(UUID ownerUserId, Pageable pageable);
    @Query("""
            select s
            from StudyPackEntity s
            where s.ownerUserId = :ownerUserId
              and (s.createdAt < :cursorCreatedAt or (s.createdAt = :cursorCreatedAt and s.id < :cursorId))
            order by s.createdAt desc, s.id desc
            """)
    List<StudyPackEntity> findByOwnerUserIdAndCursor(
            UUID ownerUserId,
            OffsetDateTime cursorCreatedAt,
            UUID cursorId,
            Pageable pageable
    );
    Optional<StudyPackEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudyPackEntity s where s.id = :id and s.ownerUserId = :ownerUserId")
    Optional<StudyPackEntity> findByIdAndOwnerUserIdForUpdate(UUID id, UUID ownerUserId);
    Optional<StudyPackEntity> findTopByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);
    long countByOwnerUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID ownerUserId,
            OffsetDateTime createdAtFromInclusive,
            OffsetDateTime createdAtToExclusive
    );
}

