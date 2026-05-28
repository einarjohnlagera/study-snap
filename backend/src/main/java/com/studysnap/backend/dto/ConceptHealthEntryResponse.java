package com.studysnap.backend.dto;

import java.time.OffsetDateTime;

public record ConceptHealthEntryResponse(
    String concept,
    OffsetDateTime lastCorrectAt,
    boolean isDue,
    Integer daysSinceReview
) {}
