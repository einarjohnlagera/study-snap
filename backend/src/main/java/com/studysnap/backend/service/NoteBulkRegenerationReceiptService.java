package com.studysnap.backend.service;

import com.studysnap.backend.repository.NoteBulkRegenerationItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Lifetime of the per-item bulk regeneration record.
 *
 * <p>⚠️ MIRRORS {@code BulkGenerationResultService.deleteExpiredReceipts} — same 24 h TTL, same hourly
 * :45 job. The record is a receipt, not audit history, and its TTL is deliberately not extended.
 *
 * <p>⚠️ UNLIKE that receipt, reading it is NOT consume-once. A regeneration batch runs far past the
 * five minutes the bulk-generation client poller tolerates, so the curator may navigate away and come
 * back; a read that deleted the row would destroy the very thing they came back for.
 */
@Service
@RequiredArgsConstructor
public class NoteBulkRegenerationReceiptService {
    private static final int RECEIPT_TTL_HOURS = 24;

    private final NoteBulkRegenerationItemRepository repository;

    /**
     * ⚠️ Expires on the BATCH's clock, so every item of a batch disappears together. Sweeping on each
     * row's own {@code updated_at} would delete a long batch's early items while its late ones
     * survived, leaving a receipt with holes in it.
     */
    @Transactional
    public long deleteExpiredItems(OffsetDateTime now) {
        return repository.deleteByBatchCreatedAtBefore(now.minusHours(RECEIPT_TTL_HOURS));
    }
}
