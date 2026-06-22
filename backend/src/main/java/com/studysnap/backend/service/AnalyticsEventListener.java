package com.studysnap.backend.service;

import com.studysnap.backend.entity.AnalyticsEventEntity;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.service.event.AnalyticsTrackingRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class AnalyticsEventListener {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventListener.class);

    private final AnalyticsEventRepository analyticsEventRepository;
    private final TaskExecutor analyticsTaskExecutor;

    public AnalyticsEventListener(
            AnalyticsEventRepository analyticsEventRepository,
            @Qualifier("analyticsTaskExecutor") TaskExecutor analyticsTaskExecutor
    ) {
        this.analyticsEventRepository = analyticsEventRepository;
        this.analyticsTaskExecutor = analyticsTaskExecutor;
    }

    // Persistence is offloaded to analyticsTaskExecutor (a separate thread where Spring Data's
    // per-save transaction applies), so no method-level transaction is needed here. AFTER_COMMIT
    // ensures the surrounding transaction has committed; fallbackExecution covers events fired
    // outside any transaction (e.g. public-endpoint events).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAnalyticsRequested(AnalyticsTrackingRequestedEvent event) {
        if (event == null || event.eventType() == null) {
            return;
        }

        try {
            analyticsTaskExecutor.execute(() -> persistEvent(event));
        } catch (Exception ex) {
            log.warn(
                    "analytics_event_dispatch_failed userId={} eventType={} entityId={}",
                    event.userId(),
                    event.eventType(),
                    event.entityId(),
                    ex
            );
        }
    }

    private void persistEvent(AnalyticsTrackingRequestedEvent requestedEvent) {
        try {
            AnalyticsEventEntity event = new AnalyticsEventEntity();
            event.setId(UUID.randomUUID());
            event.setUserId(requestedEvent.userId());
            event.setEventType(requestedEvent.eventType());
            event.setEntityId(requestedEvent.entityId());
            event.setMetadataJson(requestedEvent.metadata());
            event.setCreatedAt(OffsetDateTime.now());
            analyticsEventRepository.save(event);
        } catch (Exception ex) {
            log.warn(
                    "analytics_event_persist_failed userId={} eventType={} entityId={}",
                    requestedEvent.userId(),
                    requestedEvent.eventType(),
                    requestedEvent.entityId(),
                    ex
            );
        }
    }
}
