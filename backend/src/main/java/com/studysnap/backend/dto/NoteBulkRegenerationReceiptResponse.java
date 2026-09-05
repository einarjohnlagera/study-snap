package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

/**
 * Progress and result for one bulk regeneration batch, read by polling.
 *
 * <p>⚠️ {@code finished} is DERIVED from "no item is still PENDING or RUNNING", never from a
 * terminal flag written at the end. A driver thread killed mid-batch never reaches any end-of-batch
 * write, so a stored flag would leave such a batch permanently reporting itself in flight.
 *
 * <p>⚠️ {@code stale} says the batch has items still claiming PENDING/RUNNING but has not been
 * touched inside the staleness window, which is what a killed deploy leaves behind. Nothing sweeps a
 * lost batch — the rows expire under the 24 h TTL — so the receipt has to be able to say
 * "indeterminate" rather than showing a progress bar that will never advance.
 */
public record NoteBulkRegenerationReceiptResponse(
        UUID batchId,
        String scope,
        int totalCount,
        int regeneratedCount,
        int blockedCount,
        int failedCount,
        int notRunCount,
        int pendingCount,
        boolean finished,
        boolean stale,
        List<UUID> retryableNoteIds,
        List<NoteBulkRegenerationItemReceiptResponse> items
) {
}
