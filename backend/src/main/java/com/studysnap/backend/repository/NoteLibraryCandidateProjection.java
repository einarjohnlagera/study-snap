package com.studysnap.backend.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NoteLibraryCandidateProjection(
        UUID id,
        String title,
        String subject,
        String courseProgram,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements NoteLibrarySubjectView {
}
