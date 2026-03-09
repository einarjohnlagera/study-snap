package com.studysnap.backend.service;

import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.UserActivityEventEntity;
import com.studysnap.backend.repository.ActivityEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivityTrackingService {
    private static final Logger log = LoggerFactory.getLogger(ActivityTrackingService.class);

    private final ActivityEventRepository activityEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordActivity(UUID userId, ActivityType activityType, UUID studyPackId) {
        if (userId == null || activityType == null) {
            return;
        }

        try {
            UserActivityEventEntity event = new UserActivityEventEntity();
            event.setId(UUID.randomUUID());
            event.setUserId(userId);
            event.setStudyPackId(studyPackId);
            event.setActivityType(activityType);
            event.setCreatedAt(OffsetDateTime.now());
            activityEventRepository.save(event);
        } catch (Exception ex) {
            log.warn(
                    "activity_tracking_failed userId={} activityType={} studyPackId={}",
                    userId,
                    activityType,
                    studyPackId,
                    ex
            );
        }
    }
}
