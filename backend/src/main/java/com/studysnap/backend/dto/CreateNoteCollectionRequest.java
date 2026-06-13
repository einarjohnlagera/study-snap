package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record CreateNoteCollectionRequest(
        String title,
        String description,
        List<UUID> noteIds
) {
}
