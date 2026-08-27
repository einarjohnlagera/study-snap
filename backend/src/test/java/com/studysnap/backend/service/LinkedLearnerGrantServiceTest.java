package com.studysnap.backend.service;

import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.repository.LinkedLearnerGrantRepository;
import com.studysnap.backend.repository.LinkedLearnerRelationshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkedLearnerGrantServiceTest {
    @Mock private LinkedLearnerRelationshipRepository relationshipRepository;
    @Mock private LinkedLearnerGrantRepository grantRepository;
    @Mock private AuthService authService;

    private LinkedLearnerGrantService service;
    private UUID callerId;
    private UUID relationshipId;

    @BeforeEach
    void setUp() {
        service = new LinkedLearnerGrantService(relationshipRepository, grantRepository, authService);
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
    }
}
