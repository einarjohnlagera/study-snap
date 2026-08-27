package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.dto.LinkedLearnerActivityResponse;
import com.studysnap.backend.dto.StudyEngagementResponse;
import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.LinkedLearnerGrantScope;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkedLearnerActivityServiceTest {
    @Mock private LinkedLearnerGrantAuthorizationService authorizationService;
    @Mock private DashboardService dashboardService;
    @Mock private UserRepository userRepository;

    private LinkedLearnerActivityService service;

    @BeforeEach
    void setUp() {
        service = new LinkedLearnerActivityService(authorizationService, dashboardService, userRepository);
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

        // Viewing someone's momentum is not an activity. Pinned by exhausting EVERY collaborator:
        // authorization is a read, the only user-repository call is findById, and getStudyEngagement
        // is the sole DashboardService call. `verifyNoMoreInteractions` across all three means a save,
        // a recordActivity or a ConceptHealth touch added here later fails this test rather than
        // silently writing the owner's activity for someone else's page view.
        verify(authorizationService).requireGrant(callerId, relationshipId, LinkedLearnerGrantScope.ACTIVITY);
        verify(userRepository).findById(ownerId);
        verify(dashboardService).getStudyEngagement(ownerId);
        verifyNoMoreInteractions(authorizationService, userRepository, dashboardService);
    }

    @Test
    void activityServiceHoldsNoWriteCapableCollaborator() {
        // Structural guard rather than a behavioural one: the service cannot write activity or
        // ConceptHealth because it is not wired to anything that could. A future field that could
        // fails here at the moment it is added, which no behavioural test would catch until someone
        // actually called it.
        Set<String> collaborators = new HashSet<>();
        for (java.lang.reflect.Field field : LinkedLearnerActivityService.class.getDeclaredFields()) {
            collaborators.add(field.getType().getSimpleName());
        }
        assertThat(collaborators).containsExactlyInAnyOrder(
                "LinkedLearnerGrantAuthorizationService", "DashboardService", "UserRepository");
    }
}
