package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Lightweight owner-list representation; intentionally excludes snapshot questions and answer keys. */
public record CombinedQuizSummaryResponse(
        UUID id,
        String title,
        OffsetDateTime createdAt,
        int sectionCount,
        int questionCount,
        Sharing sharing
) {
    public enum Sharing {
        NO_LINK,
        SHARING_ON,
        SHARING_OFF
    }
}
