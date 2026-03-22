package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
@Getter
@Setter
@NoArgsConstructor
public class WebhookEventEntity {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BillingProvider provider;

    @Column(name = "event_id", nullable = false, length = 191)
    private String eventId;

    @Column(name = "event_type", length = 128)
    private String eventType;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WebhookEventStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
