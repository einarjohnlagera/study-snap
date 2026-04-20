package com.studysnap.backend.repository;

import com.studysnap.backend.entity.GeneratedQuizEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GeneratedQuizRepository extends JpaRepository<GeneratedQuizEntity, UUID> {
    Optional<GeneratedQuizEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    Optional<GeneratedQuizEntity> findByNoteId(UUID noteId);
    Optional<GeneratedQuizEntity> findByNoteIdAndOwnerUserId(UUID noteId, UUID ownerUserId);
}
