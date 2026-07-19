package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PublicLibraryCandidateProjection(
        UUID id,
        UUID ownerUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        NoteStatus status,
        boolean hasStudyPack,
        boolean hasContent,
        boolean hasSummary,
        int quizCount
) {
}
