package com.studysnap.backend.service;

import com.studysnap.backend.service.event.ReviewSetUpdatePublishedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class ReviewSetUpdateNotificationListener {
    private final ReviewSetUpdateNotificationService notificationService;
    private final TaskExecutor notificationFanOutExecutor;

    public ReviewSetUpdateNotificationListener(
            ReviewSetUpdateNotificationService notificationService,
            @Qualifier("notificationFanOutExecutor") TaskExecutor notificationFanOutExecutor
    ) {
        this.notificationService = notificationService;
        this.notificationFanOutExecutor = notificationFanOutExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReviewSetUpdatePublished(ReviewSetUpdatePublishedEvent event) {
        try {
            notificationFanOutExecutor.execute(() -> runFanOut(event));
        } catch (RuntimeException dispatchFailure) {
            log.warn(
                    "review_set_update.fan_out.dispatch_failed sourceCollectionId={} message={}",
                    event.sourceCollectionId(),
                    dispatchFailure.getMessage(),
                    dispatchFailure
            );
        }
    }

    private void runFanOut(ReviewSetUpdatePublishedEvent event) {
        try {
            notificationService.fanOut(event);
        } catch (RuntimeException fanOutFailure) {
            log.warn(
                    "review_set_update.fan_out.failed sourceCollectionId={} message={}",
                    event.sourceCollectionId(),
                    fanOutFailure.getMessage(),
                    fanOutFailure
            );
        }
    }
}
