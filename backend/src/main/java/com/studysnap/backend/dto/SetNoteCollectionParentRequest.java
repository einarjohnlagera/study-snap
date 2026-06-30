package com.studysnap.backend.dto;

import java.util.UUID;

public record SetNoteCollectionParentRequest(
        UUID parentId
) {
}
