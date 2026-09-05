package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * A bulk regeneration selection needs more note-generation units than the caller has left.
 *
 * <p>⚠️ REJECTED BEFORE ANY WORK IS DISPATCHED, and carrying the number of items to remove — the same
 * block-and-reduce policy bulk generation already uses. Running the batch until quota ran out would
 * leave the curator with a half-rebuilt Review Set and no way to tell which half.
 *
 * <p>⚠️ The requested count is the number of items that would actually be DISPATCHED, not the raw
 * selection size: blocked and not-eligible Notes spend nothing.
 */
public class BulkNoteRegenerationQuotaExceededException extends AppException {
    private static final String CODE = "BULK_NOTE_REGENERATION_QUOTA_EXCEEDED";
    private static final String MESSAGE_TEMPLATE =
            "You have %d topic note%s left this cycle. Remove %d note%s to continue.";
    private static final String PLURAL_SUFFIX = "s";
    private static final String SINGULAR_SUFFIX = "";

    private final int remaining;
    private final int requestedCount;

    public BulkNoteRegenerationQuotaExceededException(int remaining, int requestedCount) {
        super(CODE, buildMessage(remaining, requestedCount - remaining), HttpStatus.UNPROCESSABLE_ENTITY);
        this.remaining = remaining;
        this.requestedCount = requestedCount;
    }

    private static String buildMessage(int remaining, int excess) {
        return MESSAGE_TEMPLATE.formatted(
                remaining,
                remaining == 1 ? SINGULAR_SUFFIX : PLURAL_SUFFIX,
                excess,
                excess == 1 ? SINGULAR_SUFFIX : PLURAL_SUFFIX
        );
    }

    public int getRemaining() {
        return remaining;
    }

    public int getRequestedCount() {
        return requestedCount;
    }
}
