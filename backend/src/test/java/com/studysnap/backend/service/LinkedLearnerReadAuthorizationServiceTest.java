package com.studysnap.backend.service;

import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerSide;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.exception.LinkedLearnerProgressNotFoundException;
import com.studysnap.backend.repository.LinkedLearnerRelationshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkedLearnerReadAuthorizationServiceTest {
    @Mock
    private LinkedLearnerRelationshipRepository relationshipRepository;

    private LinkedLearnerReadAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new LinkedLearnerReadAuthorizationService(relationshipRepository);
    }

    @Test
    void acceptedRelationshipReturnsLearnerIdOnlyForSupporter() {
        LinkedLearnerRelationshipEntity relationship = relationship(LinkedLearnerStatus.ACCEPTED);
        when(relationshipRepository.findById(relationship.getId())).thenReturn(Optional.of(relationship));

        UUID learnerUserId = authorizationService.requireAcceptedLearnerId(
                relationship.getSupporterUserId(), relationship.getId());

        assertThat(learnerUserId).isEqualTo(relationship.getLearnerUserId());
    }

    @Test
    void pendingRelationshipGrantsNothing() {
        LinkedLearnerRelationshipEntity relationship = relationship(LinkedLearnerStatus.PENDING);
        when(relationshipRepository.findById(relationship.getId())).thenReturn(Optional.of(relationship));

        assertThatThrownBy(() -> authorizationService.requireAcceptedLearnerId(
                relationship.getSupporterUserId(), relationship.getId()))
                .isInstanceOf(LinkedLearnerProgressNotFoundException.class);
    }

    @Test
    void revokedAndNonexistentRelationshipsAreIndistinguishable() {
        LinkedLearnerRelationshipEntity relationship = relationship(LinkedLearnerStatus.REVOKED);
        UUID missingRelationshipId = UUID.randomUUID();
        when(relationshipRepository.findById(relationship.getId())).thenReturn(Optional.of(relationship));
        when(relationshipRepository.findById(missingRelationshipId)).thenReturn(Optional.empty());

        LinkedLearnerProgressNotFoundException revoked = captureNotFound(
                relationship.getSupporterUserId(), relationship.getId());
        LinkedLearnerProgressNotFoundException missing = captureNotFound(
                relationship.getSupporterUserId(), missingRelationshipId);

        assertThat(revoked.getCode()).isEqualTo(missing.getCode());
        assertThat(revoked.getStatus()).isEqualTo(missing.getStatus());
        assertThat(revoked.getMessage()).isEqualTo(missing.getMessage());
    }

    @Test
    void learnerCannotReadSupporterThroughRelationship() {
        LinkedLearnerRelationshipEntity relationship = relationship(LinkedLearnerStatus.ACCEPTED);
        when(relationshipRepository.findById(relationship.getId())).thenReturn(Optional.of(relationship));

        assertThatThrownBy(() -> authorizationService.requireAcceptedLearnerId(
                relationship.getLearnerUserId(), relationship.getId()))
                .isInstanceOf(LinkedLearnerProgressNotFoundException.class);
    }

    @Test
    void thirdPartyCannotReadRelationship() {
        LinkedLearnerRelationshipEntity relationship = relationship(LinkedLearnerStatus.ACCEPTED);
        when(relationshipRepository.findById(relationship.getId())).thenReturn(Optional.of(relationship));
        UUID thirdPartyUserId = UUID.randomUUID();

        assertThatThrownBy(() -> authorizationService.requireAcceptedLearnerId(
                thirdPartyUserId, relationship.getId()))
                .isInstanceOf(LinkedLearnerProgressNotFoundException.class);
    }

    @Test
    void revocationCutsAccessOnTheNextRead() {
        LinkedLearnerRelationshipEntity relationship = relationship(LinkedLearnerStatus.ACCEPTED);
        when(relationshipRepository.findById(relationship.getId())).thenReturn(Optional.of(relationship));

        UUID learnerUserId = authorizationService.requireAcceptedLearnerId(
                relationship.getSupporterUserId(), relationship.getId());
        relationship.setStatus(LinkedLearnerStatus.REVOKED);

        assertThat(learnerUserId).isEqualTo(relationship.getLearnerUserId());
        assertThatThrownBy(() -> authorizationService.requireAcceptedLearnerId(
                relationship.getSupporterUserId(), relationship.getId()))
                .isInstanceOf(LinkedLearnerProgressNotFoundException.class);
    }

    private LinkedLearnerProgressNotFoundException captureNotFound(UUID callerUserId, UUID relationshipId) {
        try {
            authorizationService.requireAcceptedLearnerId(callerUserId, relationshipId);
            throw new AssertionError("Expected linked learner progress to be unavailable.");
        } catch (LinkedLearnerProgressNotFoundException exception) {
            return exception;
        }
    }

    private LinkedLearnerRelationshipEntity relationship(LinkedLearnerStatus status) {
        LinkedLearnerRelationshipEntity relationship = new LinkedLearnerRelationshipEntity();
        relationship.setId(UUID.randomUUID());
        relationship.setSupporterUserId(UUID.randomUUID());
        relationship.setLearnerUserId(UUID.randomUUID());
        relationship.setStatus(status);
        relationship.setInitiatedBy(LinkedLearnerSide.SUPPORTER);
        relationship.setCreatedAt(OffsetDateTime.now());
        return relationship;
    }
}
