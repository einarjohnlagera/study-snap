package com.studysnap.backend.service;

import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.service.event.ActivityTrackingRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ActivityTrackingServiceTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private ActivityTrackingService activityTrackingService;

    @BeforeEach
    void setUp() {
        activityTrackingService = new ActivityTrackingService(applicationEventPublisher);
    }

    @Test
    void recordActivity_recordsStartedQuickReviewEvent() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();

        activityTrackingService.recordActivity(userId, ActivityType.STARTED_QUICK_REVIEW, studyPackId);

        ArgumentCaptor<ActivityTrackingRequestedEvent> captor = ArgumentCaptor.forClass(ActivityTrackingRequestedEvent.class);
        verify(applicationEventPublisher, times(1)).publishEvent(captor.capture());
        ActivityTrackingRequestedEvent published = captor.getValue();
        assertThat(published.userId()).isEqualTo(userId);
        assertThat(published.studyPackId()).isEqualTo(studyPackId);
        assertThat(published.activityType()).isEqualTo(ActivityType.STARTED_QUICK_REVIEW);
    }

    @Test
    void recordActivity_recordsCompletedQuickReviewEvent() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();

        activityTrackingService.recordActivity(userId, ActivityType.COMPLETED_QUICK_REVIEW, studyPackId);

        ArgumentCaptor<ActivityTrackingRequestedEvent> captor = ArgumentCaptor.forClass(ActivityTrackingRequestedEvent.class);
        verify(applicationEventPublisher, times(1)).publishEvent(captor.capture());
        ActivityTrackingRequestedEvent published = captor.getValue();
        assertThat(published.userId()).isEqualTo(userId);
        assertThat(published.studyPackId()).isEqualTo(studyPackId);
        assertThat(published.activityType()).isEqualTo(ActivityType.COMPLETED_QUICK_REVIEW);
    }

    @Test
    void recordActivity_doesNothingWhenMissingRequiredInputs() {
        assertThatCode(() -> activityTrackingService.recordActivity(null, ActivityType.STARTED_QUICK_REVIEW, UUID.randomUUID()))
                .doesNotThrowAnyException();
        assertThatCode(() -> activityTrackingService.recordActivity(UUID.randomUUID(), null, UUID.randomUUID()))
                .doesNotThrowAnyException();
        verifyNoInteractions(applicationEventPublisher);
    }
}
