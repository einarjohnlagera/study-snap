package com.studysnap.backend.repository;

import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.WebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEventEntity, UUID> {
    Optional<WebhookEventEntity> findByProviderAndEventId(BillingProvider provider, String eventId);
}
