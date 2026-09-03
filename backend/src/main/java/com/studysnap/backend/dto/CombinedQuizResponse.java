package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CombinedQuizResponse(
        UUID id,
        String title,
        List<CombinedQuizSection> sections,
        OffsetDateTime createdAt
) {
}
