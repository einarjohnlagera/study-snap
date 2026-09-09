package com.studysnap.backend.service;

import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.NotificationRepository;
import com.studysnap.backend.repository.ReviewSetUpdateRecipientProjection;
import com.studysnap.backend.service.event.ReviewSetUpdatePublishedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewSetUpdateNotificationServiceTest {
    @Mock
    private NoteCollectionRepository noteCollectionRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationService notificationService;

    @Test
    void oneRecipientFailureDoesNotPreventTheRemainingDelivery() {
        UUID sourceCollectionId = UUID.randomUUID();
        UUID firstLearnerId = UUID.randomUUID();
        UUID secondLearnerId = UUID.randomUUID();
        ReviewSetUpdatePublishedEvent event = new ReviewSetUpdatePublishedEvent(
                sourceCollectionId,
                Instant.parse("2026-09-09T01:50:47.123456Z")
        );
        when(noteCollectionRepository.findReviewSetUpdateRecipients(sourceCollectionId)).thenReturn(List.of(
                recipient(firstLearnerId, UUID.randomUUID()),
                recipient(secondLearnerId, UUID.randomUUID())
        ));
        when(notificationRepository.findRecipientsWithUndismissedEpisode(any(), any(), any()))
                .thenReturn(List.of());
        doAnswer(invocation -> {
            NotificationService.NotificationDelivery delivery = invocation.getArgument(0);
            if (delivery.recipientUserId().equals(firstLearnerId)) {
                throw new IllegalStateException("one insert failed");
            }
            return null;
        }).when(notificationService).deliver(any());
        ReviewSetUpdateNotificationService service = new ReviewSetUpdateNotificationService(
                noteCollectionRepository,
                notificationRepository,
                notificationService
        );

        service.fanOut(event);

        ArgumentCaptor<NotificationService.NotificationDelivery> deliveries =
                ArgumentCaptor.forClass(NotificationService.NotificationDelivery.class);
        verify(notificationService, org.mockito.Mockito.times(2)).deliver(deliveries.capture());
        assertThat(deliveries.getAllValues())
                .extracting(NotificationService.NotificationDelivery::dedupDiscriminator)
                .containsOnly(sourceCollectionId + ":" + event.publishedAt().toEpochMilli());
    }

    @Test
    void zeroAdoptersIsLegitimateAndSkipsTheSuppressionQuery() {
        UUID sourceCollectionId = UUID.randomUUID();
        when(noteCollectionRepository.findReviewSetUpdateRecipients(sourceCollectionId)).thenReturn(List.of());
        ReviewSetUpdateNotificationService service = new ReviewSetUpdateNotificationService(
                noteCollectionRepository,
                notificationRepository,
                notificationService
        );

        service.fanOut(new ReviewSetUpdatePublishedEvent(sourceCollectionId, Instant.now()));

        verify(notificationRepository, never()).findRecipientsWithUndismissedEpisode(any(), any(), any());
        verify(notificationService, never()).deliver(any());
    }

    private ReviewSetUpdateRecipientProjection recipient(UUID learnerId, UUID adoptedCollectionId) {
        return new ReviewSetUpdateRecipientProjection() {
            @Override
            public UUID getRecipientUserId() {
                return learnerId;
            }

            @Override
            public UUID getAdoptedCollectionId() {
                return adoptedCollectionId;
            }
        };
    }
}
