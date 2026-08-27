package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SharedNoteResponse(
        String id,
        String title,
        String content,
        String subject,
        String courseProgram,
        String learnerLevel,
        List<String> tags,
        String status,
        String ownerDisplayName,
        OffsetDateTime sharedAt,
        String studyPackId,
        boolean canCopy
) {
}
