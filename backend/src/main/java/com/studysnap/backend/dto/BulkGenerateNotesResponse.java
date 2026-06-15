package com.studysnap.backend.dto;

public record BulkGenerateNotesResponse(
        int acceptedTitles,
        int queuedTitles,
        int subjectCount,
        int rejectedTitles
) {
}
