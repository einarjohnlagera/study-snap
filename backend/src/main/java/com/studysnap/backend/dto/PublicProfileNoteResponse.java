package com.studysnap.backend.dto;

import java.util.List;

public record PublicProfileNoteResponse(
        String noteId,
        String title,
        String subject,
        List<String> tags,
        String contentPreview,
        String summaryPreview,
        long copyCount,
        String slug
) {
}
