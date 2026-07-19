package com.studysnap.backend.dto;

import java.util.List;

public record PublicLibraryDiscoverySectionsResponse(
        List<NoteListItemResponse> featured,
        List<NoteListItemResponse> popular,
        List<NoteListItemResponse> recent
) {
}
