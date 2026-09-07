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
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class NotificationEntity {
    @Id
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private NotificationType type;

    @Column(name = "dedup_key", nullable = false, length = 255)
    private String dedupKey;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 1000)
    private String body;

    @Column(name = "cta_label", length = 64)
    private String ctaLabel;

    @Column(name = "cta_path", length = 512)
    private String ctaPath;

    @Column(name = "announcement_id")
    private UUID announcementId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "dismissed_at")
    private OffsetDateTime dismissedAt;
}
