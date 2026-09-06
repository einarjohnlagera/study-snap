package com.studysnap.backend.dto;

import java.util.UUID;

/**
 * Handle for a queued bulk regeneration batch.
 *
 * <p>{@code batchId} addresses {@code note_bulk_regeneration_item} rows, which are written as each item
 * resolves — unlike the bulk *generation* receipt, which is a single terminal blob and cannot report a
 * batch of this length.
 */
public record BulkRegenerateNotesResponse(
        UUID batchId,
        String scope,
        int acceptedCount
) {
}
