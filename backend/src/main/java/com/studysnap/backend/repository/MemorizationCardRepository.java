package com.studysnap.backend.repository;

import com.studysnap.backend.entity.MemorizationCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SRS schedule state only. Do not inject this repository into ProgressReportService or readiness code.
 */
public interface MemorizationCardRepository extends JpaRepository<MemorizationCardEntity, UUID> {
    List<MemorizationCardEntity> findByUserIdAndStudyPackIdOrderByDueAtAscConceptAsc(UUID userId, UUID studyPackId);

    Optional<MemorizationCardEntity> findByUserIdAndStudyPackIdAndConcept(
        UUID userId,
        UUID studyPackId,
        String concept
    );

    void deleteByUserId(UUID userId);
}
