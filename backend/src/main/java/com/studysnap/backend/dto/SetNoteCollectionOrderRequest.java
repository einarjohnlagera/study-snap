package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record SetNoteCollectionOrderRequest(
        List<OrderedItem> items
) {
    public record OrderedItem(
            UUID noteId,
            String label
    ) {
    }
}
