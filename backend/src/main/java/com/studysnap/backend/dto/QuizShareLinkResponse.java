package com.studysnap.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

public record QuizShareLinkResponse(
        UUID id,
        String token,
        String shareUrl,
        @JsonProperty("isActive")
        boolean isActive,
        OffsetDateTime createdAt
) {
}
