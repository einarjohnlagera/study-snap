package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteBulkRegenerationItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteBulkRegenerationItemRepository extends JpaRepository<NoteBulkRegenerationItemEntity, UUID> {
    List<NoteBulkRegenerationItemEntity> findByBatchIdAndOwnerUserIdOrderByBatchCreatedAtAsc(
            UUID batchId,
            UUID ownerUserId
    );

    Optional<NoteBulkRegenerationItemEntity> findByBatchIdAndNoteId(UUID batchId, UUID noteId);

    /**
     * ⚠️ Sweeps on {@code batchCreatedAt}, never {@code updatedAt}, so a batch expires atomically.
     * Mirrors {@code BulkGenerationResultService.deleteExpiredReceipts} — same 24 h TTL, same hourly
     * job.
     */
    long deleteByBatchCreatedAtBefore(OffsetDateTime threshold);

    void deleteByOwnerUserId(UUID ownerUserId);
}
