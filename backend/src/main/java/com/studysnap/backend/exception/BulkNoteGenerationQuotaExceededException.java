package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class BulkNoteGenerationQuotaExceededException extends AppException {
    private static final String CODE = "BULK_NOTE_GENERATION_QUOTA_EXCEEDED";
    private static final String MESSAGE_TEMPLATE =
            "You have %d note generation(s) left this cycle. Remove %d topic(s) to continue.";

    private final int remaining;
    private final int requestedCount;

    public BulkNoteGenerationQuotaExceededException(int remaining, int requestedCount) {
        super(
                CODE,
                MESSAGE_TEMPLATE.formatted(remaining, requestedCount - remaining),
                HttpStatus.UNPROCESSABLE_ENTITY
        );
        this.remaining = remaining;
        this.requestedCount = requestedCount;
    }

    public int getRemaining() {
        return remaining;
    }

    public int getRequestedCount() {
        return requestedCount;
    }
}
