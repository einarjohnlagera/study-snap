package com.studysnap.backend.dto;

public record BulkGenerateNotesResponse(
        int acceptedTopics,
        int queuedTopics,
        int rejectedTopics
) {
}
