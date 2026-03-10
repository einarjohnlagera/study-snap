package com.studysnap.backend.repository;

import com.studysnap.backend.entity.StudyPackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyPackRepository extends JpaRepository<StudyPackEntity, UUID> {
    List<StudyPackEntity> findByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);
    Optional<StudyPackEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    Optional<StudyPackEntity> findTopByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);
}

