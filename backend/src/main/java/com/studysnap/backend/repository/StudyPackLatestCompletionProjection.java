package com.studysnap.backend.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StudyPackLatestCompletionProjection(UUID studyPackId, OffsetDateTime completedAt) {
}
