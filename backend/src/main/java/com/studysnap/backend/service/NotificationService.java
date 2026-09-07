package com.studysnap.backend.service;

import com.studysnap.backend.dto.NotificationResponse;
import com.studysnap.backend.entity.NotificationEntity;
import com.studysnap.backend.entity.NotificationType;
import com.studysnap.backend.exception.NotificationNotFoundException;
import com.studysnap.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final int MAX_INBOX_LIMIT = 100;

    private final NotificationRepository notificationRepository;

    /**
     * The unique recipient/dedup index is the idempotency guarantee. Do not add an exists check before
     * this insert: two concurrent deliveries could both pass it and still create duplicate awareness.
     */
    public NotificationResponse deliver(NotificationDelivery delivery) {
        String dedupKey = dedupKey(delivery.type(), delivery.entityId());
        NotificationEntity notification = new NotificationEntity();
        notification.setId(UUID.randomUUID());
        notification.setRecipientUserId(delivery.recipientUserId());
        notification.setType(delivery.type());
        notification.setDedupKey(dedupKey);
        notification.setTitle(delivery.title());
        notification.setBody(delivery.body());
        notification.setCtaLabel(delivery.ctaLabel());
        notification.setCtaPath(delivery.ctaPath());
        notification.setAnnouncementId(delivery.announcementId());
        notification.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        try {
            return toResponse(notificationRepository.saveAndFlush(notification));
        } catch (DataIntegrityViolationException duplicateDelivery) {
            return notificationRepository.findByRecipientUserIdAndDedupKey(delivery.recipientUserId(), dedupKey)
                    .map(this::toResponse)
                    .orElseThrow(() -> duplicateDelivery);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listInbox(UUID userId, int limit) {
        return notificationRepository.findByRecipientUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(
                        userId,
                        PageRequest.of(0, Math.min(Math.max(limit, 1), MAX_INBOX_LIMIT))
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        NotificationEntity notification = findForRecipientOrThrow(userId, notificationId);
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now(ZoneOffset.UTC));
        }
        return toResponse(notification);
    }

    @Transactional
    public NotificationResponse dismiss(UUID userId, UUID notificationId) {
        NotificationEntity notification = findForRecipientOrThrow(userId, notificationId);
        if (notification.getDismissedAt() == null) {
            notification.setDismissedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }
        return toResponse(notification);
    }

    @Transactional(readOnly = true)
    public int countActionableUnread(UUID userId) {
        return Math.toIntExact(notificationRepository.countActionableUnread(userId, NotificationType.actionableTypes()));
    }

    @Transactional
    public int deleteExpired(OffsetDateTime now, int retentionDays) {
        return notificationRepository.deleteReadOrDismissedBefore(now.minusDays(retentionDays));
    }

    public static String dedupKey(NotificationType type, UUID entityId) {
        return type.name() + ":" + entityId;
    }

    private NotificationEntity findForRecipientOrThrow(UUID userId, UUID notificationId) {
        return notificationRepository.findByIdAndRecipientUserId(notificationId, userId)
                .orElseThrow(NotificationNotFoundException::new);
    }

    private NotificationResponse toResponse(NotificationEntity notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getBody(),
                notification.getCtaLabel(),
                notification.getCtaPath(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.getDismissedAt()
        );
    }

    public record NotificationDelivery(
            UUID recipientUserId,
            NotificationType type,
            UUID entityId,
            String title,
            String body,
            String ctaLabel,
            String ctaPath,
            UUID announcementId
    ) {
    }
}
