package com.studysnap.backend.dto;

import java.util.List;

public record CreatorImpactPageResponse(
        List<NoteImpact> notes,
        int page,
        int size,
        long totalImpacted,
        long totalZeroImpact
) {
    public record NoteImpact(
            String noteId,
            String title,
            long distinctLearnersHelped,
            long viewCount,
            long copyCount
    ) {
    }
}
