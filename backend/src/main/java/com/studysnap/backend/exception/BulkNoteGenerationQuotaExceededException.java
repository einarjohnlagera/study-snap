package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class BulkNoteGenerationQuotaExceededException extends AppException {
    private static final String CODE = "BULK_NOTE_GENERATION_QUOTA_EXCEEDED";
    // Must stay word-for-word identical to the client-side form of this sentence in
    // frontend/components/notes/bulk-generation-page-client.tsx — both render into the same
    // role="alert" element, so a mismatch shows one user two vocabularies for one condition.
    private static final String MESSAGE_TEMPLATE =
            "You have %d topic note%s left this cycle. Remove %d topic%s to continue.";
    private static final String PLURAL_SUFFIX = "s";
    private static final String SINGULAR_SUFFIX = "";

    private final int remaining;
    private final int requestedCount;

    public BulkNoteGenerationQuotaExceededException(int remaining, int requestedCount) {
        super(
                CODE,
                buildMessage(remaining, requestedCount - remaining),
                HttpStatus.UNPROCESSABLE_ENTITY
        );
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
