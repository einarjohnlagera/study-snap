package com.studysnap.backend.repository;

import com.studysnap.backend.entity.ExamQuestionPoolEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamQuestionPoolRepository extends JpaRepository<ExamQuestionPoolEntity, UUID> {
    Optional<ExamQuestionPoolEntity> findByStudyPackIdAndMode(UUID studyPackId, String mode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ExamQuestionPoolEntity p where p.id = :id")
    Optional<ExamQuestionPoolEntity> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ExamQuestionPoolEntity p where p.studyPackId = :studyPackId and p.mode = :mode")
    Optional<ExamQuestionPoolEntity> findByStudyPackIdAndModeForUpdate(UUID studyPackId, String mode);

    @Query("""
            select p.id
            from ExamQuestionPoolEntity p
            where (p.generationStatus = :pendingStatus and p.generationStatusAt < :pendingCutoff)
               or (p.generationStatus = :generatingStatus and p.generationStatusAt < :generatingCutoff)
            order by p.generationStatusAt asc
            """)
    List<UUID> findStaleNonTerminalIds(
            @Param("pendingStatus") String pendingStatus,
            @Param("pendingCutoff") OffsetDateTime pendingCutoff,
            @Param("generatingStatus") String generatingStatus,
            @Param("generatingCutoff") OffsetDateTime generatingCutoff,
            Pageable pageable
    );
}
