package com.studysnap.backend.dto;

import java.time.OffsetDateTime;

public record ConceptHealthEntryResponse(
    String concept,
    OffsetDateTime lastCorrectAt,
    OffsetDateTime lastIncorrectAt,
    boolean isStruggling,
    boolean isDue,
    Integer daysSinceReview
) {}
