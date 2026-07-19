package com.studysnap.backend.dto;

import java.util.List;

public record NotesLibraryPageResponse(
        List<NoteListItemResponse> items,
        int page,
        int pageSize,
        long totalMatching,
        boolean hasMore
) {
}
