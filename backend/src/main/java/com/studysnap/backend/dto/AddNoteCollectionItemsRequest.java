package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record AddNoteCollectionItemsRequest(
        List<UUID> noteIds,
        String label
) {
    public AddNoteCollectionItemsRequest(List<UUID> noteIds) {
        this(noteIds, null);
    }
}
