package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record BulkImportResultResponse(
        List<ImportedNoteResult> created,
        List<FailedImportResult> failed
) {
    public record ImportedNoteResult(
            UUID noteId,
            String title,
            String fileName,
            boolean lowConfidence
    ) {
    }

    public record FailedImportResult(
            String fileName,
            String errorCode,
            String message
    ) {
    }
}
