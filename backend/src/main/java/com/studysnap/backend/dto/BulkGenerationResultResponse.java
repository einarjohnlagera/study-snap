package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BulkGenerationResultResponse(
        UUID id,
        String subject,
        String courseProgram,
        String targetProfileType,
        boolean makePublic,
        int requestedCount,
        int createdCount,
        List<String> failedTopics,
        List<String> quotaBlockedTopics,
        OffsetDateTime createdAt
) {
}
