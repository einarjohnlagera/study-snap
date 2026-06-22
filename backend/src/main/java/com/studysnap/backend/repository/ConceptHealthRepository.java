package com.studysnap.backend.repository;

import com.studysnap.backend.entity.ConceptHealthEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConceptHealthRepository extends JpaRepository<ConceptHealthEntity, UUID> {
    void deleteByUserId(UUID userId);

    List<ConceptHealthEntity> findByUserIdAndStudyPackId(UUID userId, UUID studyPackId);

    List<ConceptHealthEntity> findByUserIdAndStudyPackIdIn(UUID userId, List<UUID> studyPackIds);

    Optional<ConceptHealthEntity> findByUserIdAndStudyPackIdAndConcept(
        UUID userId,
        UUID studyPackId,
        String concept
    );
}
