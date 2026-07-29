package com.studysnap.backend.dto;

import java.util.List;

public record CreatorImpactResponse(
        long distinctLearnersHelped,
        List<NoteImpact> notes
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
