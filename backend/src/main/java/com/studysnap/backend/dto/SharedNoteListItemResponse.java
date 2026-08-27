package com.studysnap.backend.dto;

import java.time.OffsetDateTime;

public record SharedNoteListItemResponse(
        String noteId,
        String title,
        String subject,
        String ownerDisplayName,
        boolean studyPackReady,
        OffsetDateTime sharedAt
) {
}
