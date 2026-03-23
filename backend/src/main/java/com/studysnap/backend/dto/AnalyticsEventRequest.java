package com.studysnap.backend.dto;

import com.studysnap.backend.entity.AnalyticsEventType;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record AnalyticsEventRequest(
        @NotNull AnalyticsEventType eventType,
        UUID entityId,
        Map<String, Object> metadata
) {
}
