package com.studysnap.backend.repository;

import com.studysnap.backend.entity.StudyPackEntity;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyPackRepository extends JpaRepository<StudyPackEntity, UUID> {
    List<StudyPackEntity> findByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);
    Optional<StudyPackEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudyPackEntity s where s.id = :id and s.ownerUserId = :ownerUserId")
    Optional<StudyPackEntity> findByIdAndOwnerUserIdForUpdate(UUID id, UUID ownerUserId);
    Optional<StudyPackEntity> findTopByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);
}

