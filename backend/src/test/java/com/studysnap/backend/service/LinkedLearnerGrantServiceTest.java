package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.repository.LinkedLearnerGrantRepository;
import com.studysnap.backend.repository.LinkedLearnerRelationshipRepository;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkedLearnerGrantServiceTest {
    @Mock private LinkedLearnerRelationshipRepository relationshipRepository;
    @Mock private LinkedLearnerGrantRepository grantRepository;
    @Mock private AuthService authService;
    @Mock private AnalyticsService analyticsService;

    private LinkedLearnerGrantService service;
    private UUID callerId;
    private UUID relationshipId;

    @BeforeEach
    void setUp() {
        service = new LinkedLearnerGrantService(
                relationshipRepository, grantRepository, authService, analyticsService);
        callerId = UUID.randomUUID();
        relationshipId = UUID.randomUUID();
        LinkedLearnerRelationshipEntity relationship = new LinkedLearnerRelationshipEntity();
        relationship.setId(relationshipId);
        relationship.setSupporterUserId(callerId);
        relationship.setLearnerUserId(UUID.randomUUID());
        relationship.setStatus(LinkedLearnerStatus.ACCEPTED);
        when(relationshipRepository.findById(relationshipId)).thenReturn(Optional.of(relationship));
    }

    @Test
    void repeatedEnableAndDisableRequestsAreNoOpSafe() {
        when(grantRepository.insertLiveIfAbsent(any(), eq(relationshipId), eq(callerId), any(), eq("ACTIVITY"), any()))
                .thenReturn(1, 0);
        when(grantRepository.revokeLive(eq(relationshipId), eq(callerId), any(), any()))
                .thenReturn(1, 0);

        assertThat(service.setActivityGrant(callerId, relationshipId, true).granted()).isTrue();
        assertThat(service.setActivityGrant(callerId, relationshipId, true).granted()).isTrue();
        assertThat(service.setActivityGrant(callerId, relationshipId, false).granted()).isFalse();
        assertThat(service.setActivityGrant(callerId, relationshipId, false).granted()).isFalse();

        verify(grantRepository, times(2)).insertLiveIfAbsent(
                any(), eq(relationshipId), eq(callerId), any(), eq("ACTIVITY"), any());
        verify(grantRepository, times(2)).revokeLive(eq(relationshipId), eq(callerId), any(), any());
        verify(analyticsService).trackEvent(
                callerId,
                AnalyticsEventType.CONNECTION_ACTIVITY_SHARED,
                relationshipId,
                Map.of("role", "SUPPORTER")
        );
        verify(analyticsService).trackEvent(
                callerId,
                AnalyticsEventType.CONNECTION_ACTIVITY_SHARE_REVOKED,
                relationshipId,
                Map.of("role", "SUPPORTER")
        );
        verifyNoMoreInteractions(analyticsService);
    }

    @Test
    void grantEventMetadataContainsOnlyCallerRole() throws Exception {
        when(grantRepository.insertLiveIfAbsent(
                any(), eq(relationshipId), eq(callerId), any(), eq("ACTIVITY"), any()))
                .thenReturn(1);

        service.setActivityGrant(callerId, relationshipId, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(analyticsService).trackEvent(
                eq(callerId),
                eq(AnalyticsEventType.CONNECTION_ACTIVITY_SHARED),
                eq(relationshipId),
                metadataCaptor.capture()
        );
        Set<String> keys = new HashSet<>();
        new ObjectMapper().valueToTree(metadataCaptor.getValue()).fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactly("role");
        assertThat(metadataCaptor.getValue()).containsEntry("role", "SUPPORTER");
    }

    @Test
    void failedGrantWriteEmitsNoEvent() {
        when(grantRepository.insertLiveIfAbsent(
                any(), eq(relationshipId), eq(callerId), any(), eq("ACTIVITY"), any()))
                .thenThrow(new IllegalStateException("write failed"));

        assertThatThrownBy(() -> service.setActivityGrant(callerId, relationshipId, true))
                .isInstanceOf(IllegalStateException.class);

        verify(analyticsService, never()).trackEvent(any(), any(), any(), any());
    }

    @Test
    void analyticsFailureDoesNotFailGrantTransition() {
        when(grantRepository.insertLiveIfAbsent(
                any(), eq(relationshipId), eq(callerId), any(), eq("ACTIVITY"), any()))
                .thenReturn(1);
        doThrow(new IllegalStateException("analytics unavailable"))
                .when(analyticsService)
                .trackEvent(any(), any(), any(), any());

        assertThatCode(() -> service.setActivityGrant(callerId, relationshipId, true))
                .doesNotThrowAnyException();
    }
}
