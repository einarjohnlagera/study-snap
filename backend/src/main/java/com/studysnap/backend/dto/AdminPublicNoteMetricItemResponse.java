package com.studysnap.backend.dto;

import java.util.UUID;

public record AdminPublicNoteMetricItemResponse(
        UUID noteId,
        String title,
        String subject,
        long totalCount
) {
}
