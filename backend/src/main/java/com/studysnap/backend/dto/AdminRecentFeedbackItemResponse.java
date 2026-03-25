package com.studysnap.backend.dto;

import com.studysnap.backend.entity.FeedbackStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminRecentFeedbackItemResponse(
        UUID feedbackId,
        String userEmail,
        String message,
        String pageUrl,
        FeedbackStatus status,
        OffsetDateTime createdAt
) {
}
