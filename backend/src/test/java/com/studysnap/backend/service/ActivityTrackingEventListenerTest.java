package com.studysnap.backend.service;

import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.entity.UserActivityEventEntity;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.event.ActivityTrackingRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityTrackingEventListenerTest {

    @Mock
    private ActivityEventRepository activityEventRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private UserRepository userRepository;

    private ActivityTrackingEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ActivityTrackingEventListener(activityEventRepository, studyPackRepository, userRepository);
        when(activityEventRepository.save(any(UserActivityEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void onActivityRequested_updatesStreakForMeaningfulActivity() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setCurrentStreak(2);
        user.setLongestStreak(4);
        user.setLastStudyDate(LocalDate.now().minusDays(1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        listener.onActivityRequested(new ActivityTrackingRequestedEvent(
                userId,
                ActivityType.COMPLETED_QUICK_REVIEW,
                UUID.randomUUID()
        ));

        verify(activityEventRepository, times(1)).save(any(UserActivityEventEntity.class));
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        UserEntity savedUser = userCaptor.getValue();
        assertThat(savedUser.getCurrentStreak()).isEqualTo(3);
        assertThat(savedUser.getLongestStreak()).isEqualTo(4);
        assertThat(savedUser.getLastStudyDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void onActivityRequested_doesNotIncrementStreakForSameStudyDay() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setCurrentStreak(3);
        user.setLongestStreak(5);
        user.setLastStudyDate(LocalDate.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        listener.onActivityRequested(new ActivityTrackingRequestedEvent(
                userId,
                ActivityType.CREATED_STUDY_PACK,
                UUID.randomUUID()
        ));

        verify(activityEventRepository, times(1)).save(any(UserActivityEventEntity.class));
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void onActivityRequested_ignoresNonMeaningfulActivityForStreak() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        user.setCurrentStreak(2);
        user.setLongestStreak(5);
        user.setLastStudyDate(LocalDate.now().minusDays(1));

        listener.onActivityRequested(new ActivityTrackingRequestedEvent(
                userId,
                ActivityType.OPENED_STUDY_PACK,
                UUID.randomUUID()
        ));

        verify(activityEventRepository, times(1)).save(any(UserActivityEventEntity.class));
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    private UserEntity buildUser(UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("learner@example.com");
        user.setPasswordHash("hashed");
        user.setFirstName("Learner");
        user.setDisplayName("Learner");
        user.setEngagementMode(EngagementMode.FOCUSED);
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);
        user.setCurrentStreak(0);
        user.setLongestStreak(0);
        user.setCreatedAt(OffsetDateTime.now().minusDays(10));
        user.setUpdatedAt(OffsetDateTime.now().minusDays(1));
        return user;
    }
}
