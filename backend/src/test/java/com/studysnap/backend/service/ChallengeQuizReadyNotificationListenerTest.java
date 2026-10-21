package com.studysnap.backend.service;

import com.studysnap.backend.service.event.ChallengeQuizReadyEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChallengeQuizReadyNotificationListenerTest {
    @Test
    void deliveryFailureDoesNotEscapeTheReadyTransition() {
        NotificationService service = mock(NotificationService.class);
        when(service.deliver(any(NotificationService.NotificationDelivery.class)))
                .thenThrow(new IllegalStateException("inbox unavailable"));
        TaskExecutor directExecutor = Runnable::run;
        var listener = new ChallengeQuizReadyNotificationListener(service, directExecutor);
        var event = new ChallengeQuizReadyEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThatCode(() -> listener.onReady(event)).doesNotThrowAnyException();
        verify(service).deliver(any(NotificationService.NotificationDelivery.class));
    }
}
