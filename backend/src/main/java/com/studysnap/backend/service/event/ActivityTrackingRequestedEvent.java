package com.studysnap.backend.service.event;

import com.studysnap.backend.entity.ActivityType;

import java.util.UUID;

public record ActivityTrackingRequestedEvent(
        UUID userId,
        ActivityType activityType,
        UUID studyPackId
) {
}
