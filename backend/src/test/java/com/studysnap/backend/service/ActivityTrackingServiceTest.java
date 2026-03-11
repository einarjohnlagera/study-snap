package com.studysnap.backend.service;

import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.UserActivityEventEntity;
import com.studysnap.backend.repository.ActivityEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ActivityTrackingServiceTest {

    @Mock
    private ActivityEventRepository activityEventRepository;

    private ActivityTrackingService activityTrackingService;

    @BeforeEach
    void setUp() {
        activityTrackingService = new ActivityTrackingService(activityEventRepository);
        lenient().when(activityEventRepository.save(org.mockito.Mockito.any(UserActivityEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordActivity_recordsStartedQuickReviewEvent() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();

        activityTrackingService.recordActivity(userId, ActivityType.STARTED_QUICK_REVIEW, studyPackId);

        ArgumentCaptor<UserActivityEventEntity> captor = ArgumentCaptor.forClass(UserActivityEventEntity.class);
        verify(activityEventRepository, times(1)).save(captor.capture());
        UserActivityEventEntity saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getStudyPackId()).isEqualTo(studyPackId);
        assertThat(saved.getActivityType()).isEqualTo(ActivityType.STARTED_QUICK_REVIEW);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void recordActivity_recordsCompletedQuickReviewEvent() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();

        activityTrackingService.recordActivity(userId, ActivityType.COMPLETED_QUICK_REVIEW, studyPackId);

        ArgumentCaptor<UserActivityEventEntity> captor = ArgumentCaptor.forClass(UserActivityEventEntity.class);
        verify(activityEventRepository, times(1)).save(captor.capture());
        UserActivityEventEntity saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getStudyPackId()).isEqualTo(studyPackId);
        assertThat(saved.getActivityType()).isEqualTo(ActivityType.COMPLETED_QUICK_REVIEW);
    }

    @Test
    void recordActivity_doesNotThrowWhenRepositoryFails() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        doThrow(new RuntimeException("db unavailable"))
                .when(activityEventRepository)
                .save(org.mockito.Mockito.any(UserActivityEventEntity.class));

        assertThatCode(() -> activityTrackingService.recordActivity(
                userId,
                ActivityType.STARTED_QUICK_REVIEW,
                studyPackId
        )).doesNotThrowAnyException();

        verify(activityEventRepository, times(1)).save(org.mockito.Mockito.any(UserActivityEventEntity.class));
    }
}
