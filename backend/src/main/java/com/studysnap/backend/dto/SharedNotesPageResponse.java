package com.studysnap.backend.dto;

import java.util.List;

public record SharedNotesPageResponse(
        List<SharedNoteListItemResponse> items,
        String nextCursor,
        boolean hasMore
) {
}
