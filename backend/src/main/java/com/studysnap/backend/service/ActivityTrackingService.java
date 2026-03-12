package com.studysnap.backend.service;

import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.service.event.ActivityTrackingRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivityTrackingService {
    private final ApplicationEventPublisher eventPublisher;

    public void recordActivity(UUID userId, ActivityType activityType, UUID studyPackId) {
        if (userId == null || activityType == null) {
            return;
        }
        eventPublisher.publishEvent(new ActivityTrackingRequestedEvent(userId, activityType, studyPackId));
    }
}
