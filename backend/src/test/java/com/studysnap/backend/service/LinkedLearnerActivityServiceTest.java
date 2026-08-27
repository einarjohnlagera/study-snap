package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.dto.LinkedLearnerActivityResponse;
import com.studysnap.backend.dto.StudyEngagementResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.LinkedLearnerGrantScope;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.LinkedLearnerNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkedLearnerActivityServiceTest {
    @Mock private LinkedLearnerGrantAuthorizationService authorizationService;
    @Mock private DashboardService dashboardService;
    @Mock private UserRepository userRepository;
    @Mock private AnalyticsService analyticsService;

    private LinkedLearnerActivityService service;

    @BeforeEach
    void setUp() {
        service = new LinkedLearnerActivityService(
                authorizationService, dashboardService, userRepository, analyticsService);
    }

    @Test
    void activityReadReusesExistingEngagementAndExposesOnlyRatifiedFields() {
        UUID callerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        UserEntity owner = new UserEntity();
        owner.setId(ownerId);
        owner.setDisplayName("Learner Name");
        when(authorizationService.requireGrant(callerId, relationshipId, LinkedLearnerGrantScope.ACTIVITY))
                .thenReturn(ownerId);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(dashboardService.getStudyEngagement(ownerId))
                .thenReturn(new StudyEngagementResponse(EngagementMode.CONSISTENCY, 3, 9, 4));

        LinkedLearnerActivityResponse response = service.getActivity(callerId, relationshipId);
        Set<String> keys = new HashSet<>();
        new ObjectMapper().valueToTree(response).fieldNames().forEachRemaining(keys::add);

        assertThat(keys).containsExactlyInAnyOrder(
                "displayName", "engagementMode", "currentStreak", "longestStreak", "studyDaysThisWeek");
        assertThat(response.currentStreak()).isEqualTo(3);
        verify(dashboardService).getStudyEngagement(ownerId);
        verify(analyticsService).trackEvent(
                callerId,
                AnalyticsEventType.CONNECTION_ACTIVITY_VIEWED,
                relationshipId,
                Map.of()
        );
        verifyNoMoreInteractions(dashboardService);
    }

    @Test
    void activityReadWritesNothingForEitherParty() {
        UUID callerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        UserEntity owner = new UserEntity();
        owner.setId(ownerId);
        owner.setDisplayName("Learner Name");
        when(authorizationService.requireGrant(callerId, relationshipId, LinkedLearnerGrantScope.ACTIVITY))
                .thenReturn(ownerId);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(dashboardService.getStudyEngagement(ownerId))
                .thenReturn(new StudyEngagementResponse(EngagementMode.FOCUSED, 0, 0, 0));

        service.getActivity(callerId, relationshipId);

        // Viewing someone's momentum is not a learning activity. Pinned by exhausting EVERY collaborator:
        // authorization is a read, the only user-repository call is findById, and getStudyEngagement
        // is the sole DashboardService call. AnalyticsService writes only product telemetry after the
        // read succeeds; it cannot write UserActivityEventEntity, ConceptHealth or either user's state.
        // `verifyNoMoreInteractions` across all four means a save,
        // a recordActivity or a ConceptHealth touch added here later fails this test rather than
        // silently writing the owner's activity for someone else's page view.
        verify(authorizationService).requireGrant(callerId, relationshipId, LinkedLearnerGrantScope.ACTIVITY);
        verify(userRepository).findById(ownerId);
        verify(dashboardService).getStudyEngagement(ownerId);
        verify(analyticsService).trackEvent(
                callerId,
                AnalyticsEventType.CONNECTION_ACTIVITY_VIEWED,
                relationshipId,
                Map.of()
        );
        verifyNoMoreInteractions(authorizationService, userRepository, dashboardService, analyticsService);
    }

    @Test
    void activityServiceHoldsNoLearningStateWriteCapableCollaborator() {
        // Structural guard rather than a behavioural one: the service cannot write learning activity or
        // ConceptHealth because it is not wired to any learning-state writer. AnalyticsService is the
        // deliberate telemetry-only exception introduced for the Phase 2 grant-to-view funnel.
        Set<String> collaborators = new HashSet<>();
        for (java.lang.reflect.Field field : LinkedLearnerActivityService.class.getDeclaredFields()) {
            // ⚠️ INSTANCE collaborators only. Statics are constants and loggers, not things the service
            // can write learning state through — Lombok's @Slf4j `log` is a private static final Logger,
            // and enumerating it here would make this guard fail on a logging change while telling us
            // nothing about write capability.
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            collaborators.add(field.getType().getSimpleName());
        }
        assertThat(collaborators).containsExactlyInAnyOrder(
                "LinkedLearnerGrantAuthorizationService", "DashboardService", "UserRepository", "AnalyticsService");
    }

    @Test
    void deniedActivityReadEmitsNoViewEvent() {
        UUID callerId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        when(authorizationService.requireGrant(callerId, relationshipId, LinkedLearnerGrantScope.ACTIVITY))
                .thenThrow(new LinkedLearnerNotFoundException());

        assertThatThrownBy(() -> service.getActivity(callerId, relationshipId))
                .isInstanceOf(LinkedLearnerNotFoundException.class);

        verify(analyticsService, never()).trackEvent(any(), any(), any(), any());
        verifyNoInteractions(dashboardService, userRepository);
    }

    @Test
    void viewEventMetadataContainsNoLearningContent() throws Exception {
        UUID callerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        UserEntity owner = new UserEntity();
        owner.setId(ownerId);
        owner.setDisplayName("Learner Name");
        when(authorizationService.requireGrant(callerId, relationshipId, LinkedLearnerGrantScope.ACTIVITY))
                .thenReturn(ownerId);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(dashboardService.getStudyEngagement(ownerId))
                .thenReturn(new StudyEngagementResponse(EngagementMode.FOCUSED, 4, 12, 3));

        service.getActivity(callerId, relationshipId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(analyticsService).trackEvent(
                eq(callerId),
                eq(AnalyticsEventType.CONNECTION_ACTIVITY_VIEWED),
                eq(relationshipId),
                metadataCaptor.capture()
        );
        String serializedMetadata = new ObjectMapper().writeValueAsString(metadataCaptor.getValue());
        assertThat(serializedMetadata)
                .isEqualTo("{}")
                .doesNotContain(
                        "streak", "studyDays", "score", "mastery", "concept", "noteTitle", "studyPackTitle");
    }

    @Test
    void analyticsFailureDoesNotFailAuthorizedActivityRead() {
        UUID callerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        UserEntity owner = new UserEntity();
        owner.setId(ownerId);
        owner.setDisplayName("Learner Name");
        when(authorizationService.requireGrant(callerId, relationshipId, LinkedLearnerGrantScope.ACTIVITY))
                .thenReturn(ownerId);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(dashboardService.getStudyEngagement(ownerId))
                .thenReturn(new StudyEngagementResponse(EngagementMode.FOCUSED, 0, 0, 0));
        doThrow(new IllegalStateException("analytics unavailable"))
                .when(analyticsService)
                .trackEvent(any(), any(), any(), any());

        assertThatCode(() -> service.getActivity(callerId, relationshipId))
                .doesNotThrowAnyException();
    }
}
