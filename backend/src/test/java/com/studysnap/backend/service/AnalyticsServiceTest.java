package com.studysnap.backend.service;

import com.studysnap.backend.dto.AdminAnalyticsSummaryResponse;
import com.studysnap.backend.entity.AnalyticsEventEntity;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private AnalyticsEventRepository analyticsEventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private StudyPackRepository studyPackRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        TaskExecutor inlineExecutor = Runnable::run;
        analyticsService = new AnalyticsService(
                analyticsEventRepository,
                userRepository,
                noteRepository,
                studyPackRepository,
                inlineExecutor
        );
    }

    @Test
    void trackEvent_persistsAnalyticsEvent() {
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        analyticsService.trackEvent(
                userId,
                AnalyticsEventType.LANDING_PAGE_VIEWED,
                entityId,
                Map.of("page", "landing")
        );

        ArgumentCaptor<AnalyticsEventEntity> captor = ArgumentCaptor.forClass(AnalyticsEventEntity.class);
        verify(analyticsEventRepository).save(captor.capture());
        AnalyticsEventEntity saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getEntityId()).isEqualTo(entityId);
        assertThat(saved.getEventType()).isEqualTo(AnalyticsEventType.LANDING_PAGE_VIEWED);
        assertThat(saved.getMetadataJson()).containsEntry("page", "landing");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void trackEvent_swallowsPersistenceFailures() {
        when(analyticsEventRepository.save(any(AnalyticsEventEntity.class)))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> analyticsService.trackEvent(
                UUID.randomUUID(),
                AnalyticsEventType.DEMO_OPENED,
                null,
                Map.of("page", "demo")
        )).doesNotThrowAnyException();
    }

    @Test
    void getSummary_aggregatesAnalyticsAndCoreCounts() {
        when(analyticsEventRepository.countByEventType(AnalyticsEventType.LANDING_PAGE_VIEWED)).thenReturn(12L);
        when(analyticsEventRepository.countByEventType(AnalyticsEventType.LANDING_CTA_CLICKED)).thenReturn(7L);
        when(analyticsEventRepository.countByEventType(AnalyticsEventType.PUBLIC_NOTE_VIEWED)).thenReturn(30L);
        when(analyticsEventRepository.countByEventType(AnalyticsEventType.PUBLIC_NOTE_COPY_CLICKED)).thenReturn(8L);
        when(analyticsEventRepository.countByEventType(AnalyticsEventType.SIGNUP_STARTED)).thenReturn(6L);
        when(analyticsEventRepository.countByEventType(AnalyticsEventType.SIGNUP_COMPLETED)).thenReturn(4L);
        when(analyticsEventRepository.countByEventType(AnalyticsEventType.EMAIL_VERIFIED)).thenReturn(3L);
        when(userRepository.count()).thenReturn(20L);
        when(noteRepository.count()).thenReturn(45L);
        when(studyPackRepository.count()).thenReturn(18L);
        when(analyticsEventRepository.countByEventType(AnalyticsEventType.CHALLENGE_QUIZ_STARTED)).thenReturn(11L);
        when(analyticsEventRepository.countByEventType(AnalyticsEventType.ADAPTIVE_PRACTICE_STARTED)).thenReturn(9L);
        when(analyticsEventRepository.countByEventType(AnalyticsEventType.SUBSCRIPTION_STARTED)).thenReturn(5L);

        AdminAnalyticsSummaryResponse summary = analyticsService.getSummary();

        assertThat(summary.landingPageViews()).isEqualTo(12L);
        assertThat(summary.landingCtaClicks()).isEqualTo(7L);
        assertThat(summary.publicNoteViews()).isEqualTo(30L);
        assertThat(summary.publicNoteCopyClicks()).isEqualTo(8L);
        assertThat(summary.signupsStarted()).isEqualTo(6L);
        assertThat(summary.signupsCompleted()).isEqualTo(4L);
        assertThat(summary.emailVerificationsCompleted()).isEqualTo(3L);
        assertThat(summary.totalUsers()).isEqualTo(20L);
        assertThat(summary.totalNotes()).isEqualTo(45L);
        assertThat(summary.totalStudyPacksGenerated()).isEqualTo(18L);
        assertThat(summary.totalChallengeQuizzes()).isEqualTo(11L);
        assertThat(summary.totalAdaptiveQuizzes()).isEqualTo(9L);
        assertThat(summary.totalUpgrades()).isEqualTo(5L);
    }
}
