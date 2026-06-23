package com.studysnap.backend.repository;

import com.studysnap.backend.entity.BulkGenerationResultEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface BulkGenerationResultRepository extends JpaRepository<BulkGenerationResultEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BulkGenerationResultEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    long deleteByCreatedAtBefore(OffsetDateTime threshold);

    void deleteByOwnerUserId(UUID ownerUserId);
}
