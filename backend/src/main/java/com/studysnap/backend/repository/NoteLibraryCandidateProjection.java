package com.studysnap.backend.repository;

import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NoteLibraryCandidateProjection(
        UUID id,
        String title,
        String subject,
        String courseProgram,
        DomainContext domainContext,
        LearnerLevel learnerLevel,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements NoteLibrarySubjectView {
}
