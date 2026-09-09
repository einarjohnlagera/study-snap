package com.studysnap.backend.service;

import com.studysnap.backend.entity.AnnouncementEntity;
import com.studysnap.backend.service.event.ReviewSetUpdatePublishedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ⚠️ R9 — THE INVARIANT THIS RELEASE RESTS ON, GUARDED THE ONLY WAY IT ACTUALLY CAN BE.
 *
 * <p>{@link NotificationService#deliver} catches {@link org.springframework.dao.DataIntegrityViolationException}
 * from the recipient/dedup unique index and re-reads the winning row. Under an ambient transaction that
 * violation marks the whole transaction rollback-only, so ONE duplicate recipient would take an ENTIRE
 * fan-out down. {@code publish}, {@code fanOut} and {@code deliver} are therefore deliberately not
 * transactional.
 *
 * <p>⚠️⚠️ WHY THIS TEST EXISTS AS A REFLECTION CHECK RATHER THAN A RUNTIME ONE, AND WHY THE RUNTIME ONE
 * IS NOT ENOUGH — established by mutation, not by argument. {@code AnnouncementServiceIntegrationTest}
 * asserts {@code TransactionSynchronizationManager.isActualTransactionActive()} is false during
 * delivery, which reads like the real guard. It is not: that test builds the service with {@code new},
 * so there is NO Spring AOP proxy and {@code @Transactional} is INERT. Adding {@code @Transactional} to
 * {@code fanOut} left all 20 of its tests GREEN.
 *
 * <p>This is the repo's recurring "the guard must reach its subject the way production does" failure
 * arriving from the opposite end — the fixture cannot express the state it claims to forbid. A
 * reflection check over the declared annotations cannot be fooled that way, because the annotation is
 * exactly what production reads.
 *
 * <p>⚠️ If a future producer genuinely needs a transaction, it must NOT be obtained by annotating these
 * methods. Delete this test only alongside a redesign of the dedup catch itself.
 */
class FanOutTransactionBoundaryTest {

    @Test
    void announcementPublishAndFanOutAreNotTransactional() throws NoSuchMethodException {
        Method publish = AnnouncementService.class.getDeclaredMethod("publish", UUID.class);
        Method fanOut = AnnouncementService.class.getDeclaredMethod(
                "fanOut", AnnouncementEntity.class, List.class
        );

        assertThat(publish.isAnnotationPresent(Transactional.class))
                .as("publish must not be transactional — R9")
                .isFalse();
        assertThat(fanOut.isAnnotationPresent(Transactional.class))
                .as("fanOut must not be transactional — one duplicate key would poison the whole fan-out")
                .isFalse();
        assertThat(AnnouncementService.class.isAnnotationPresent(Transactional.class))
                .as("a class-level @Transactional would apply to fanOut just as surely")
                .isFalse();
    }

    @Test
    void notificationDeliverIsNotTransactional() throws NoSuchMethodException {
        Method deliver = NotificationService.class.getDeclaredMethod(
                "deliver", NotificationService.NotificationDelivery.class
        );

        assertThat(deliver.isAnnotationPresent(Transactional.class))
                .as("deliver owns the DataIntegrityViolationException catch; a transaction breaks it")
                .isFalse();
        assertThat(NotificationService.class.isAnnotationPresent(Transactional.class))
                .as("NotificationService annotates individual read/write methods, never the class")
                .isFalse();
    }

    @Test
    void reviewSetUpdateFanOutIsNotTransactional() throws NoSuchMethodException {
        Method fanOut = ReviewSetUpdateNotificationService.class.getDeclaredMethod(
                "fanOut", ReviewSetUpdatePublishedEvent.class
        );

        assertThat(fanOut.isAnnotationPresent(Transactional.class))
                .as("Review Set fan-out must not let one dedup conflict poison every recipient")
                .isFalse();
        assertThat(ReviewSetUpdateNotificationService.class.isAnnotationPresent(Transactional.class))
                .as("a class-level transaction would apply to Review Set fan-out")
                .isFalse();
    }

    @Test
    void reviewSetUpdateListenerRunsAfterCommitWithFallbackExecution() throws NoSuchMethodException {
        Method listener = ReviewSetUpdateNotificationListener.class.getDeclaredMethod(
                "onReviewSetUpdatePublished", ReviewSetUpdatePublishedEvent.class
        );
        TransactionalEventListener annotation = listener.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isTrue();
    }
}
