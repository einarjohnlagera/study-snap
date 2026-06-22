package com.studysnap.backend.service.event;

import com.studysnap.backend.entity.AnalyticsEventType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record AnalyticsTrackingRequestedEvent(
        UUID userId,
        AnalyticsEventType eventType,
        UUID entityId,
        Map<String, Object> metadata
) {
    public AnalyticsTrackingRequestedEvent {
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
