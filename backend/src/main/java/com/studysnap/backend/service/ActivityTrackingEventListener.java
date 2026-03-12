package com.studysnap.backend.service;

import com.studysnap.backend.entity.UserActivityEventEntity;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.service.event.ActivityTrackingRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ActivityTrackingEventListener {
    private static final Logger log = LoggerFactory.getLogger(ActivityTrackingEventListener.class);

    private final ActivityEventRepository activityEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onActivityRequested(ActivityTrackingRequestedEvent event) {
        if (event == null || event.userId() == null || event.activityType() == null) {
            return;
        }

        try {
            UserActivityEventEntity activityEvent = new UserActivityEventEntity();
            activityEvent.setId(UUID.randomUUID());
            activityEvent.setUserId(event.userId());
            activityEvent.setStudyPackId(event.studyPackId());
            activityEvent.setActivityType(event.activityType());
            activityEvent.setCreatedAt(OffsetDateTime.now());
            activityEventRepository.save(activityEvent);
        } catch (Exception ex) {
            log.warn(
                    "activity_tracking_failed userId={} activityType={} studyPackId={}",
                    event.userId(),
                    event.activityType(),
                    event.studyPackId(),
                    ex
            );
        }
    }
}
