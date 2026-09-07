package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * ⚠️ SIZES ARE PINNED AT THE API BOUNDARY, not left to the column, so an over-long body fails as a
 * validation error naming the field instead of a database truncation error.
 */
public record UpsertAnnouncementRequest(
        @NotBlank(message = "title is required.")
        @Size(max = 255, message = "title must be 255 characters or less.")
        String title,

        @NotBlank(message = "body is required.")
        @Size(max = 1000, message = "body must be 1000 characters or less.")
        String body,

        @Size(max = 64, message = "ctaLabel must be 64 characters or less.")
        String ctaLabel,

        @Size(max = 512, message = "ctaPath must be 512 characters or less.")
        String ctaPath,

        @NotNull(message = "audience is required.")
        String audience,

        @Size(max = 64, message = "audienceValue must be 64 characters or less.")
        String audienceValue,

        OffsetDateTime expiresAt
) {
}
