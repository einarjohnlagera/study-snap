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
}
