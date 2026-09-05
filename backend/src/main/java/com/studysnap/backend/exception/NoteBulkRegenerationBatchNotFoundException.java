package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * No bulk regeneration batch with that id belongs to the caller.
 *
 * <p>⚠️ ONE CONTRACT FOR THREE CASES — unknown id, another user's batch, and a batch already swept
 * by the 24 h TTL — so a batch id cannot be used to probe whether someone else ran one.
 */
public class NoteBulkRegenerationBatchNotFoundException extends AppException {
    public NoteBulkRegenerationBatchNotFoundException() {
        super(
                "NOTE_BULK_REGENERATION_BATCH_NOT_FOUND",
                "That regeneration batch is no longer available.",
                HttpStatus.NOT_FOUND
        );
    }
}
