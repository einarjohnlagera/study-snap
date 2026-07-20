package com.studysnap.backend.dto;

import java.time.OffsetDateTime;

public record NoteQuickReviewLastReviewedResponse(String noteId, OffsetDateTime lastReviewedAt) {
}
