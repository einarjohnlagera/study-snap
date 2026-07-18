package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NoteStatusProjection(
        UUID id,
        NoteStatus status,
        OffsetDateTime updatedAt
) {
}
