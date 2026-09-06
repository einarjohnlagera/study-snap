package com.studysnap.backend.dto;

import java.util.UUID;

/**
 * One item's outcome in a bulk regeneration batch.
 *
 * <p>{@code title} is nullable on purpose: a Note deleted mid-batch still leaves a readable
 * {@code NOT_RUN} row (failure-matrix row 7), and the receipt must be able to report it rather than
 * dropping the item because its Note is gone.
 */
public record NoteBulkRegenerationItemReceiptResponse(
        UUID noteId,
        String title,
        String state,
        String reasonCode,
        String reason,
        boolean shareLinkDeactivated
) {
}
