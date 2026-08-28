package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.entity.LinkedLearnerGrantEntity;
import com.studysnap.backend.entity.LinkedLearnerGrantScope;
import com.studysnap.backend.exception.LinkedLearnerProgressGrantNotAllowedException;
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
import com.studysnap.backend.exception.LinkedLearnerNotFoundException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkedLearnerGrantServiceTest {
    private static final String ACTIVITY_SCOPE = "ACTIVITY";
    private static final String PROGRESS_SCOPE = "PROGRESS";
    @Mock private LinkedLearnerRelationshipRepository relationshipRepository;
    @Mock private LinkedLearnerGrantRepository grantRepository;
    @Mock private AuthService authService;
    @Mock private AnalyticsService analyticsService;

    private LinkedLearnerGrantService service;
    private UUID callerId;
    private UUID relationshipId;
    private LinkedLearnerRelationshipEntity relationship;

    @BeforeEach
    void setUp() {
        service = new LinkedLearnerGrantService(
                relationshipRepository, grantRepository, authService, analyticsService);
        callerId = UUID.randomUUID();
        relationshipId = UUID.randomUUID();
        relationship = new LinkedLearnerRelationshipEntity();
        relationship.setId(relationshipId);
        relationship.setSupporterUserId(callerId);
        relationship.setLearnerUserId(UUID.randomUUID());
        relationship.setStatus(LinkedLearnerStatus.ACCEPTED);
        when(relationshipRepository.findById(relationshipId)).thenReturn(Optional.of(relationship));
    }

    @Test
    void grantingIsRefusedOnAPausedRelationshipButWITHDRAWINGIsNot() {
        // ⚠️ The asymmetry is the point. A downward birth-year correction pauses ACCEPTED → PENDING and
        // the live grant row survives the pause, so gating BOTH branches on ACCEPTED locked the learner
        // out of turning sharing OFF at exactly the moment the privacy question became urgent — and
        // re-acceptance would then restore readability with no fresh act of sharing.
        relationship.setStatus(LinkedLearnerStatus.PENDING);

        assertThatThrownBy(() -> service.setActivityGrant(callerId, relationshipId, true))
                .isInstanceOf(LinkedLearnerNotFoundException.class);

        when(grantRepository.revokeLive(eq(relationshipId), eq(callerId), any(), any())).thenReturn(1);
        assertThat(service.setActivityGrant(callerId, relationshipId, false).granted()).isFalse();
        verify(grantRepository).revokeLive(eq(relationshipId), eq(callerId), any(), any());
    }

    @Test
    void aNonPartyCallerCannotTouchTheGrantInEitherDirection() {
        // Membership is checked on BOTH branches; knowing a relationship id must never be enough.
        UUID stranger = UUID.randomUUID();

        assertThatThrownBy(() -> service.setActivityGrant(stranger, relationshipId, true))
                .isInstanceOf(LinkedLearnerNotFoundException.class);
        assertThatThrownBy(() -> service.setActivityGrant(stranger, relationshipId, false))
                .isInstanceOf(LinkedLearnerNotFoundException.class);
        verifyNoMoreInteractions(grantRepository);
    }

    @Test
    void theLEARNERSideOfTheGrantWriteResolvesTheSupporterAsRecipient() {
        // Every other test in this class has the SUPPORTER as caller, so the learner-side write path
        // had no coverage at all — and a resolveOtherParty that ignored the caller would have produced
        // from_user_id == to_user_id, violating ck_linked_learner_grants_not_self at the database.
        UUID learnerId = relationship.getLearnerUserId();
        UUID supporterId = relationship.getSupporterUserId();
        when(grantRepository.insertLiveIfAbsent(
                any(), eq(relationshipId), eq(learnerId), eq(supporterId), eq(ACTIVITY_SCOPE), any()))
                .thenReturn(1);

        assertThat(service.setActivityGrant(learnerId, relationshipId, true).granted()).isTrue();

        verify(grantRepository).insertLiveIfAbsent(
                any(), eq(relationshipId), eq(learnerId), eq(supporterId), eq(ACTIVITY_SCOPE), any());
    }

    @Test
    void repeatedEnableAndDisableRequestsAreNoOpSafe() {
        when(grantRepository.insertLiveIfAbsent(
                any(), eq(relationshipId), eq(callerId), any(), eq(ACTIVITY_SCOPE), any()))
                .thenReturn(1, 0);
        when(grantRepository.revokeLive(eq(relationshipId), eq(callerId), any(), any()))
                .thenReturn(1, 0);
        when(grantRepository.findFirstByRelationshipIdAndFromUserIdAndScopeAndRevokedAtIsNull(
                relationshipId, callerId, LinkedLearnerGrantScope.ACTIVITY))
                .thenReturn(Optional.of(liveGrant(
                        callerId, relationship.getLearnerUserId(), LinkedLearnerGrantScope.ACTIVITY)));

        assertThat(service.setActivityGrant(callerId, relationshipId, true).granted()).isTrue();
        assertThat(service.setActivityGrant(callerId, relationshipId, true).granted()).isTrue();
        assertThat(service.setActivityGrant(callerId, relationshipId, false).granted()).isFalse();
        assertThat(service.setActivityGrant(callerId, relationshipId, false).granted()).isFalse();

        verify(grantRepository, times(2)).insertLiveIfAbsent(
                any(), eq(relationshipId), eq(callerId), any(), eq(ACTIVITY_SCOPE), any());
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
    void supporterCannotGrantProgressAndNoRowIsWritten() {
        assertThatThrownBy(() -> service.setProgressGrant(callerId, relationshipId, true))
                .isInstanceOf(LinkedLearnerProgressGrantNotAllowedException.class);

        verifyNoMoreInteractions(grantRepository);
    }

    @Test
    void learnerProgressGrantAndRevokeEmitOnlyRealTransitionEvents() {
        UUID learnerId = relationship.getLearnerUserId();
        UUID supporterId = relationship.getSupporterUserId();
        when(grantRepository.insertLiveIfAbsent(
                any(), eq(relationshipId), eq(learnerId), eq(supporterId), eq(PROGRESS_SCOPE), any()))
                .thenReturn(1, 0);
        when(grantRepository.findFirstByRelationshipIdAndFromUserIdAndScopeAndRevokedAtIsNull(
                relationshipId, learnerId, LinkedLearnerGrantScope.PROGRESS))
                .thenReturn(Optional.of(liveGrant(learnerId, supporterId, LinkedLearnerGrantScope.PROGRESS)));
        when(grantRepository.revokeLive(
                eq(relationshipId), eq(learnerId), eq(LinkedLearnerGrantScope.PROGRESS), any()))
                .thenReturn(1, 0);

        assertThat(service.setProgressGrant(learnerId, relationshipId, true).granted()).isTrue();
        assertThat(service.setProgressGrant(learnerId, relationshipId, true).granted()).isTrue();
        assertThat(service.setProgressGrant(learnerId, relationshipId, false).granted()).isFalse();
        assertThat(service.setProgressGrant(learnerId, relationshipId, false).granted()).isFalse();

        verify(analyticsService).trackEvent(
                learnerId,
                AnalyticsEventType.CONNECTION_PROGRESS_SHARED,
                relationshipId,
                Map.of("role", "LEARNER"));
        verify(analyticsService).trackEvent(
                learnerId,
                AnalyticsEventType.CONNECTION_PROGRESS_SHARE_REVOKED,
                relationshipId,
                Map.of("role", "LEARNER"));
        verifyNoMoreInteractions(analyticsService);
    }

    @Test
    void zeroRowInsertWithoutALiveGrantReportsNotFound() {
        UUID learnerId = relationship.getLearnerUserId();
        when(grantRepository.insertLiveIfAbsent(
                any(), eq(relationshipId), eq(learnerId), eq(callerId), eq(PROGRESS_SCOPE), any()))
                .thenReturn(0);
        when(grantRepository.findFirstByRelationshipIdAndFromUserIdAndScopeAndRevokedAtIsNull(
                relationshipId, learnerId, LinkedLearnerGrantScope.PROGRESS))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setProgressGrant(learnerId, relationshipId, true))
                .isInstanceOf(LinkedLearnerNotFoundException.class);
        verify(analyticsService, never()).trackEvent(any(), any(), any(), any());
    }

    /**
     * ⚠️ Pins the {@code toUserId} cross-check inside the zero-row recheck. Deleting that
     * {@code .filter(...)} previously left the whole suite green: without it, a live row pointing at a
     * DIFFERENT counterparty is accepted as proof that this caller's grant exists, and the write is
     * reported as success. Added at the v0.93.0 pre-signoff pressure test.
     */
    @Test
    void zeroRowInsertWithALiveGrantToADifferentCounterpartyReportsNotFound() {
        UUID learnerId = relationship.getLearnerUserId();
        LinkedLearnerGrantEntity foreignGrant = new LinkedLearnerGrantEntity();
        foreignGrant.setId(UUID.randomUUID());
        foreignGrant.setRelationshipId(relationshipId);
        foreignGrant.setFromUserId(learnerId);
        foreignGrant.setToUserId(UUID.randomUUID());
        foreignGrant.setScope(LinkedLearnerGrantScope.PROGRESS);
        when(grantRepository.insertLiveIfAbsent(
                any(), eq(relationshipId), eq(learnerId), eq(callerId), eq(PROGRESS_SCOPE), any()))
                .thenReturn(0);
        when(grantRepository.findFirstByRelationshipIdAndFromUserIdAndScopeAndRevokedAtIsNull(
                relationshipId, learnerId, LinkedLearnerGrantScope.PROGRESS))
                .thenReturn(Optional.of(foreignGrant));

        assertThatThrownBy(() -> service.setProgressGrant(learnerId, relationshipId, true))
                .isInstanceOf(LinkedLearnerNotFoundException.class);
        verify(analyticsService, never()).trackEvent(any(), any(), any(), any());
    }

    @Test
    void grantEventMetadataContainsOnlyCallerRole() throws Exception {
        when(grantRepository.insertLiveIfAbsent(
                any(), eq(relationshipId), eq(callerId), any(), eq(ACTIVITY_SCOPE), any()))
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
                any(), eq(relationshipId), eq(callerId), any(), eq(ACTIVITY_SCOPE), any()))
                .thenThrow(new IllegalStateException("write failed"));

        assertThatThrownBy(() -> service.setActivityGrant(callerId, relationshipId, true))
                .isInstanceOf(IllegalStateException.class);

        verify(analyticsService, never()).trackEvent(any(), any(), any(), any());
    }

    @Test
    void analyticsFailureDoesNotFailGrantTransition() {
        when(grantRepository.insertLiveIfAbsent(
                any(), eq(relationshipId), eq(callerId), any(), eq(ACTIVITY_SCOPE), any()))
                .thenReturn(1);
        doThrow(new IllegalStateException("analytics unavailable"))
                .when(analyticsService)
                .trackEvent(any(), any(), any(), any());

        assertThatCode(() -> service.setActivityGrant(callerId, relationshipId, true))
                .doesNotThrowAnyException();
    }

    private LinkedLearnerGrantEntity liveGrant(
            UUID fromUserId,
            UUID toUserId,
            LinkedLearnerGrantScope scope
    ) {
        LinkedLearnerGrantEntity grant = new LinkedLearnerGrantEntity();
        grant.setId(UUID.randomUUID());
        grant.setRelationshipId(relationshipId);
        grant.setFromUserId(fromUserId);
        grant.setToUserId(toUserId);
        grant.setScope(scope);
        return grant;
    }
}
