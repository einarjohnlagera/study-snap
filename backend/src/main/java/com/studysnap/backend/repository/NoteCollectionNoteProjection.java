package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteVisibility;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NoteCollectionNoteProjection(
        UUID noteId,
        String title,
        String subject,
        String courseProgram,
        NoteStatus status,
        NoteVisibility visibility,
        OffsetDateTime updatedAt
) {
}
