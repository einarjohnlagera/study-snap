package com.studysnap.backend.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NoteLatestCompletionProjection(UUID noteId, OffsetDateTime completedAt) {
}
