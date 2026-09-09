package com.studysnap.backend.service;

import com.studysnap.backend.entity.NotificationType;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.NotificationRepository;
import com.studysnap.backend.repository.ReviewSetUpdateRecipientProjection;
import com.studysnap.backend.service.event.ReviewSetUpdatePublishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewSetUpdateNotificationService {
    static final String TITLE = "This Review Set has been updated";
    static final String BODY = "Open your Review Set to review and apply the latest published changes.";
    static final String CTA_LABEL = "View Review Set";

    private final NoteCollectionRepository noteCollectionRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    /**
     * Runs after publication commit on {@code notificationFanOutExecutor}; it must remain outside an
     * ambient transaction so one dedup conflict cannot poison the rest of the fan-out.
     */
    public void fanOut(ReviewSetUpdatePublishedEvent event) {
        List<ReviewSetUpdateRecipientProjection> recipients = noteCollectionRepository
                .findReviewSetUpdateRecipients(event.sourceCollectionId());
        if (recipients.isEmpty()) {
            return;
        }

        String sourcePrefix = NotificationType.REVIEW_SET_UPDATE.name()
                + ":" + event.sourceCollectionId() + ":";
        String dedupDiscriminator = event.sourceCollectionId() + ":" + event.publishedAt().toEpochMilli();
        List<UUID> recipientUserIds = recipients.stream()
                .map(ReviewSetUpdateRecipientProjection::getRecipientUserId)
                .toList();

        // This batch filter compares different publication events, never the identity of the same
        // delivery. The unique index remains the sole same-event idempotency mechanism. Its worst race
        // is one extra signal for a genuinely newer revision, so it fails open to awareness.
        Set<UUID> suppressedRecipientIds = new HashSet<>(notificationRepository.findRecipientsWithUndismissedEpisode(
                NotificationType.REVIEW_SET_UPDATE,
                recipientUserIds,
                sourcePrefix + "%"
        ));

        for (ReviewSetUpdateRecipientProjection recipient : recipients) {
            if (suppressedRecipientIds.contains(recipient.getRecipientUserId())) {
                continue;
            }
            deliverOne(recipient, dedupDiscriminator, event.sourceCollectionId());
        }
    }

    private void deliverOne(
            ReviewSetUpdateRecipientProjection recipient,
            String dedupDiscriminator,
            UUID sourceCollectionId
    ) {
        try {
            notificationService.deliver(new NotificationService.NotificationDelivery(
                    recipient.getRecipientUserId(),
                    NotificationType.REVIEW_SET_UPDATE,
                    dedupDiscriminator,
                    TITLE,
                    BODY,
                    CTA_LABEL,
                    "/collections/" + recipient.getAdoptedCollectionId(),
                    null
            ));
        } catch (RuntimeException failedDelivery) {
            log.warn(
                    "review_set_update.fan_out.failed sourceCollectionId={} recipientUserId={} message={}",
                    sourceCollectionId,
                    recipient.getRecipientUserId(),
                    failedDelivery.getMessage(),
                    failedDelivery
            );
        }
    }
}
