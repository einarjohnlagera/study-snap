package com.studysnap.backend.service;

import com.studysnap.backend.entity.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BulkOperationNotificationServiceTest {
    @Mock
    private NotificationService notificationService;

    private BulkOperationNotificationService service;

    @BeforeEach
    void setUp() {
        service = new BulkOperationNotificationService(notificationService);
    }

    @Test
    void generationCopyCoversFailureAndQuotaCasesWithSingularAndPluralTitles() {
        assertGeneration(
                List.of("Renal function"), List.of(),
                "1 topic couldn't be generated",
                "Couldn't be generated: Renal function"
        );
        assertGeneration(
                List.of("Renal function", "Fluid balance"), List.of(),
                "2 topics couldn't be generated",
                "Couldn't be generated: Renal function, Fluid balance"
        );
        assertGeneration(
                List.of(), List.of("Renal function"),
                "1 topic needs more monthly capacity",
                "Not created because your monthly limit was reached: Renal function"
        );
        assertGeneration(
                List.of(), List.of("Renal function", "Fluid balance"),
                "2 topics need more monthly capacity",
                "Not created because your monthly limit was reached: Renal function, Fluid balance"
        );
        assertGeneration(
                List.of("Renal function"), List.of("Fluid balance"),
                "Some topics couldn't be generated",
                "Couldn't be generated: Renal function. Not created because your monthly limit was reached: Fluid balance."
        );
    }

    @Test
    void regenerationCopyCoversEveryCompletionOutcome() {
        assertRegeneration(1, 1, "Your Study Pack has been updated", "Open your Library to see it.");
        assertRegeneration(3, 3, "3 Study Packs have been updated", "Open your Library to see them.");
        assertRegeneration(
                3, 1,
                "1 Study Pack has been updated",
                "Some Study Packs weren't updated. Your existing Study Packs are unchanged and still work."
        );
        assertRegeneration(
                4, 2,
                "2 Study Packs have been updated",
                "Some Study Packs weren't updated. Your existing Study Packs are unchanged and still work."
        );
        assertRegeneration(
                3, 0,
                "We couldn't update your Study Packs",
                "Your existing Study Packs are unchanged and still work."
        );
    }

    @Test
    void generationTopicBudgetOmitsWholeTopicsAndReportsHowManyRemain() {
        String topic = "x".repeat(160);

        service.bulkGenerationIncomplete(UUID.randomUUID(), UUID.randomUUID(), List.of(
                topic + "1", topic + "2", topic + "3", topic + "4", topic + "5", topic + "6"
        ), List.of());

        NotificationService.NotificationDelivery delivery = capturedDelivery();
        assertThat(delivery.body()).endsWith(" and 1 more");
        assertThat(delivery.body()).doesNotContain(topic + "6");
        assertThat(delivery.body().length()).isLessThanOrEqualTo(1000);
    }

    @Test
    void oneOversizedTopicIsShortenedWithAnEllipsisBeforePersistence() {
        service.bulkGenerationIncomplete(
                UUID.randomUUID(), UUID.randomUUID(), List.of("x".repeat(1_200)), List.of()
        );

        NotificationService.NotificationDelivery delivery = capturedDelivery();
        assertThat(delivery.body()).startsWith("Couldn't be generated: ").endsWith("…");
        assertThat(delivery.body()).hasSize("Couldn't be generated: ".length() + 850);
    }

    @Test
    void deliveryFailureIsLoggedAndSwallowed() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(notificationService).deliver(any(NotificationService.NotificationDelivery.class));

        assertThatCode(() -> service.bulkRegenerationComplete(
                UUID.randomUUID(), UUID.randomUUID(), 1, 1
        )).doesNotThrowAnyException();
    }

    private void assertGeneration(
            List<String> failed,
            List<String> quotaBlocked,
            String expectedTitle,
            String expectedBody
    ) {
        clearInvocations(notificationService);
        service = new BulkOperationNotificationService(notificationService);
        UUID resultId = UUID.randomUUID();
        service.bulkGenerationIncomplete(UUID.randomUUID(), resultId, failed, quotaBlocked);
        assertDelivery(NotificationType.BULK_GENERATION_INCOMPLETE, expectedTitle, expectedBody, resultId);
    }

    private void assertRegeneration(int requested, int regenerated, String expectedTitle, String expectedBody) {
        clearInvocations(notificationService);
        service = new BulkOperationNotificationService(notificationService);
        UUID batchId = UUID.randomUUID();
        service.bulkRegenerationComplete(UUID.randomUUID(), batchId, requested, regenerated);
        assertDelivery(NotificationType.BULK_REGENERATION_COMPLETE, expectedTitle, expectedBody, batchId);
    }

    private void assertDelivery(NotificationType type, String title, String body, UUID operationId) {
        NotificationService.NotificationDelivery delivery = capturedDelivery();
        assertThat(delivery.type()).isEqualTo(type);
        // The bare operation id is the ONLY idempotency input: NotificationService prefixes the type, and
        // a discriminator that varies per call would let a re-run deliver a second notification.
        assertThat(delivery.dedupDiscriminator()).isEqualTo(operationId.toString());
        assertThat(delivery.title()).isEqualTo(title);
        assertThat(delivery.body()).isEqualTo(body);
        assertThat(delivery.ctaLabel()).isEqualTo("Open Library");
        assertThat(delivery.ctaPath()).isEqualTo("/library");
        assertThat(delivery.announcementId()).isNull();
    }

    private NotificationService.NotificationDelivery capturedDelivery() {
        ArgumentCaptor<NotificationService.NotificationDelivery> captor =
                ArgumentCaptor.forClass(NotificationService.NotificationDelivery.class);
        verify(notificationService).deliver(captor.capture());
        return captor.getValue();
    }
}
