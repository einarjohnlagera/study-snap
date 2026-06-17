package com.studysnap.backend.dto;

import java.util.UUID;

public record BulkGenerateNotesResponse(
        UUID resultId,
        int acceptedTopics,
        int queuedTopics,
        int rejectedTopics
) {
}
