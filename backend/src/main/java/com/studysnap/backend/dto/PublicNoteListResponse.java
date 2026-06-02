package com.studysnap.backend.dto;

import java.util.List;

public record PublicNoteListResponse(
        List<NoteListItemResponse> items,
        int total
) {
}
