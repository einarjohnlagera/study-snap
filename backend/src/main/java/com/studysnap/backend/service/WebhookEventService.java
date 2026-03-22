package com.studysnap.backend.service;

import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.WebhookEventEntity;
import com.studysnap.backend.entity.WebhookEventStatus;
import com.studysnap.backend.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebhookEventService {
    private final WebhookEventRepository webhookEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<WebhookEventEntity> reserveEvent(
            BillingProvider provider,
            String eventId,
            String eventType
    ) {
        WebhookEventEntity event = new WebhookEventEntity();
        event.setId(UUID.randomUUID());
        event.setProvider(provider);
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setStatus(WebhookEventStatus.RECEIVED);
        event.setCreatedAt(OffsetDateTime.now());
        event.setProcessedAt(null);

        try {
            return Optional.of(webhookEventRepository.save(event));
        } catch (DataIntegrityViolationException ex) {
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(UUID webhookEventId) {
        webhookEventRepository.findById(webhookEventId).ifPresent(event -> {
            event.setStatus(WebhookEventStatus.PROCESSED);
            event.setProcessedAt(OffsetDateTime.now());
            webhookEventRepository.save(event);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID webhookEventId) {
        webhookEventRepository.findById(webhookEventId).ifPresent(event -> {
            event.setStatus(WebhookEventStatus.FAILED);
            event.setProcessedAt(OffsetDateTime.now());
            webhookEventRepository.save(event);
        });
    }
}
