package com.studysnap.backend.dto;

import java.util.List;

public record PublicProfileNoteResponse(
        String noteId,
        String title,
        String courseProgram,
        List<String> applicablePrograms,
        String domainContext,
        String learnerLevel,
        String subject,
        List<String> tags,
        String contentPreview,
        String summaryPreview,
        long copyCount,
        long shareCount,
        long viewCount,
        String slug
) {
}
