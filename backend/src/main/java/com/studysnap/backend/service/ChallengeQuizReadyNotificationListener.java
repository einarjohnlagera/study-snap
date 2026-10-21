package com.studysnap.backend.service;

import com.studysnap.backend.service.event.ChallengeQuizReadyEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class ChallengeQuizReadyNotificationListener {
    private final NotificationService notificationService;
    private final TaskExecutor executor;

    public ChallengeQuizReadyNotificationListener(NotificationService notificationService,
            @Qualifier("notificationFanOutExecutor") TaskExecutor executor) {
        this.notificationService = notificationService;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReady(ChallengeQuizReadyEvent event) {
        try {
            executor.execute(() -> {
                try {
                    notificationService.deliver(ChallengeQuizReadyNotifications.delivery(
                            event.userId(), event.noteId(), event.sessionId()));
                } catch (Exception exception) {
                    log.warn("challenge_quiz_ready.notification_failed sessionId={}", event.sessionId(), exception);
                }
            });
        } catch (Exception exception) {
            log.warn("challenge_quiz_ready.dispatch_failed sessionId={}", event.sessionId(), exception);
        }
    }
}
