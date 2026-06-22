package com.studysnap.backend.repository;

import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.LearnerLevel;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeneratedQuizRepository extends JpaRepository<GeneratedQuizEntity, UUID> {
    Optional<GeneratedQuizEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    Optional<GeneratedQuizEntity> findByNoteId(UUID noteId);
    Optional<GeneratedQuizEntity> findByNoteIdAndOwnerUserId(UUID noteId, UUID ownerUserId);
    List<GeneratedQuizEntity> findByOwnerUserIdAndNoteIdIn(UUID ownerUserId, List<UUID> noteIds);
    void deleteByOwnerUserId(UUID ownerUserId);
    List<GeneratedQuizEntity> findByNoteIdAndTargetLearnerLevelIsNotNullOrderByGeneratedAtDesc(
            UUID noteId,
            Pageable pageable
    );

    default Optional<LearnerLevel> findLatestTargetLearnerLevelByNoteId(UUID noteId) {
        return findByNoteIdAndTargetLearnerLevelIsNotNullOrderByGeneratedAtDesc(
                noteId,
                PageRequest.of(0, 1)
        ).stream()
                .map(GeneratedQuizEntity::getTargetLearnerLevel)
                .findFirst();
    }
}
