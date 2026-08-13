package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record CreateNoteCollectionRequest(
        String title,
        String description,
        List<UUID> noteIds,
        String learnerLevel
) {
    public CreateNoteCollectionRequest(String title, String description, List<UUID> noteIds) {
        this(title, description, noteIds, null);
    }
}
