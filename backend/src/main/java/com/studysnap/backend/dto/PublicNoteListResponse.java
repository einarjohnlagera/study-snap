package com.studysnap.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicNoteListResponse(
        List<NoteListItemResponse> items,
        int total,
        Integer page,
        Integer pageSize,
        Long totalMatching,
        Boolean hasMore
) {
    public PublicNoteListResponse(List<NoteListItemResponse> items, int total) {
        this(items, total, null, null, null, null);
    }
}
