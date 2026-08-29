package com.studysnap.backend.service;

import com.studysnap.backend.entity.LinkedLearnerGrantEntity;
import com.studysnap.backend.entity.LinkedLearnerGrantScope;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerSide;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.LinkedLearnerNotFoundException;
import com.studysnap.backend.repository.LinkedLearnerGrantRepository;
import com.studysnap.backend.repository.LinkedLearnerGuardianConsentRepository;
import com.studysnap.backend.repository.LinkedLearnerRelationshipRepository;
import com.studysnap.backend.repository.UserRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkedLearnerGrantAuthorizationServiceTest {
    private static final UUID SUPPORTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LEARNER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID RELATIONSHIP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock private LinkedLearnerRelationshipRepository relationshipRepository;
    @Mock private LinkedLearnerGrantRepository grantRepository;
    @Mock private LinkedLearnerGuardianConsentRepository consentRepository;
    @Mock private UserRepository userRepository;
    @Mock private GuardianConsentPolicy guardianConsentPolicy;
    @Mock private AuthService authService;

    private LinkedLearnerGrantAuthorizationService service;
    // An ACCEPTED relationship always carries the learner's recorded birth year — acceptance
    // requires it. Fixtures model that rather than a null, which is a state this service denies.
    private static final int ADULT_BIRTH_YEAR = 1990;

    private LinkedLearnerRelationshipEntity relationship;

    @BeforeEach
    void setUp() {
        service = new LinkedLearnerGrantAuthorizationService(
                relationshipRepository, grantRepository, consentRepository, userRepository,
                guardianConsentPolicy, authService);
        relationship = new LinkedLearnerRelationshipEntity();
        relationship.setId(RELATIONSHIP_ID);
        relationship.setSupporterUserId(SUPPORTER_ID);
        relationship.setLearnerUserId(LEARNER_ID);
        relationship.setStatus(LinkedLearnerStatus.ACCEPTED);
        relationship.setInitiatedBy(LinkedLearnerSide.SUPPORTER);
        relationship.setCreatedAt(OffsetDateTime.now());
        when(relationshipRepository.findById(RELATIONSHIP_ID)).thenReturn(Optional.of(relationship));
    }

    @Test
    void directionalGrantLetsRecipientReadOwnerButNotTheReverse() {
        LinkedLearnerGrantEntity learnerToSupporter = grant(LEARNER_ID, SUPPORTER_ID);
        when(grantRepository.findFirstByRelationshipIdAndFromUserIdAndScopeAndRevokedAtIsNull(
                RELATIONSHIP_ID, LEARNER_ID, LinkedLearnerGrantScope.ACTIVITY))
                .thenReturn(Optional.of(learnerToSupporter));
        when(userRepository.findById(LEARNER_ID)).thenReturn(Optional.of(user(LEARNER_ID, ADULT_BIRTH_YEAR)));

        UUID sharedUserId = service.requireGrant(
                SUPPORTER_ID, RELATIONSHIP_ID, LinkedLearnerGrantScope.ACTIVITY);

        assertThat(sharedUserId).isEqualTo(LEARNER_ID);
        assertThatThrownBy(() -> service.requireGrant(
                LEARNER_ID, RELATIONSHIP_ID, LinkedLearnerGrantScope.ACTIVITY))
                .isInstanceOf(LinkedLearnerNotFoundException.class);
        verify(authService).requireEmailVerified(SUPPORTER_ID);
    }

    @Test
    void acceptedRelationshipWithoutGrantStillDeniesAccess() {
        assertThatThrownBy(() -> service.requireGrant(
                SUPPORTER_ID, RELATIONSHIP_ID, LinkedLearnerGrantScope.ACTIVITY))
                .isInstanceOf(LinkedLearnerNotFoundException.class);
    }

    @Test
    void relationshipLeavingAcceptedCutsTheNextRead() {
        when(grantRepository.findFirstByRelationshipIdAndFromUserIdAndScopeAndRevokedAtIsNull(
                RELATIONSHIP_ID, LEARNER_ID, LinkedLearnerGrantScope.ACTIVITY))
                .thenReturn(Optional.of(grant(LEARNER_ID, SUPPORTER_ID)));
        when(userRepository.findById(LEARNER_ID)).thenReturn(Optional.of(user(LEARNER_ID, ADULT_BIRTH_YEAR)));
        assertThat(service.requireGrant(SUPPORTER_ID, RELATIONSHIP_ID, LinkedLearnerGrantScope.ACTIVITY))
                .isEqualTo(LEARNER_ID);

        relationship.setStatus(LinkedLearnerStatus.PENDING);

        assertThatThrownBy(() -> service.requireGrant(
                SUPPORTER_ID, RELATIONSHIP_ID, LinkedLearnerGrantScope.ACTIVITY))
                .isInstanceOf(LinkedLearnerNotFoundException.class);
    }

    @Test
    void expiredAndRevokedRelationshipsUseTheSameNotFoundAccessContract() {
        relationship.setStatus(LinkedLearnerStatus.REVOKED);
        assertThatThrownBy(() -> service.requireGrant(
                SUPPORTER_ID, RELATIONSHIP_ID, LinkedLearnerGrantScope.ACTIVITY))
                .isInstanceOf(LinkedLearnerNotFoundException.class);

        relationship.setStatus(LinkedLearnerStatus.EXPIRED);
        assertThatThrownBy(() -> service.requireGrant(
                SUPPORTER_ID, RELATIONSHIP_ID, LinkedLearnerGrantScope.ACTIVITY))
                .isInstanceOf(LinkedLearnerNotFoundException.class);
    }

    @Test
    void guardianConsentAppliesToLearnerDataButNotSupporterData() {
        UserEntity minorLearner = user(LEARNER_ID, 2015);
        when(userRepository.findById(LEARNER_ID)).thenReturn(Optional.of(minorLearner));
        when(guardianConsentPolicy.requiresGuardianConsent(2015)).thenReturn(true);
        when(consentRepository.findByRelationshipId(RELATIONSHIP_ID)).thenReturn(Optional.empty());
        when(grantRepository.findFirstByRelationshipIdAndFromUserIdAndScopeAndRevokedAtIsNull(
                RELATIONSHIP_ID, LEARNER_ID, LinkedLearnerGrantScope.PROGRESS))
                .thenReturn(Optional.of(grant(
                        LEARNER_ID, SUPPORTER_ID, LinkedLearnerGrantScope.PROGRESS)));
        when(grantRepository.findFirstByRelationshipIdAndFromUserIdAndScopeAndRevokedAtIsNull(
                RELATIONSHIP_ID, SUPPORTER_ID, LinkedLearnerGrantScope.ACTIVITY))
                .thenReturn(Optional.of(grant(SUPPORTER_ID, LEARNER_ID)));

        assertThatThrownBy(() -> service.requireGrant(
                SUPPORTER_ID, RELATIONSHIP_ID, LinkedLearnerGrantScope.PROGRESS))
                .isInstanceOf(LinkedLearnerNotFoundException.class);
        assertThat(service.requireGrant(LEARNER_ID, RELATIONSHIP_ID, LinkedLearnerGrantScope.ACTIVITY))
                .isEqualTo(SUPPORTER_ID);
        verify(authService).requireEmailVerified(LEARNER_ID);
    }

    @Test
    void learnerDataWithAnUnknownBirthYearIsDeniedRatherThanWaved() {
        // Defence in depth, and the direction matters: an ACCEPTED relationship always carries the
        // learner's birth year today, so this can deny nobody. It exists so that a future grant path
        // producing ACCEPTED without one cannot silently skip the consent gate.
        when(userRepository.findById(LEARNER_ID)).thenReturn(Optional.of(user(LEARNER_ID, null)));
        when(grantRepository.findFirstByRelationshipIdAndFromUserIdAndScopeAndRevokedAtIsNull(
                RELATIONSHIP_ID, LEARNER_ID, LinkedLearnerGrantScope.ACTIVITY))
                .thenReturn(Optional.of(grant(LEARNER_ID, SUPPORTER_ID)));

        assertThatThrownBy(() -> service.requireGrant(
                SUPPORTER_ID, RELATIONSHIP_ID, LinkedLearnerGrantScope.ACTIVITY))
                .isInstanceOf(LinkedLearnerNotFoundException.class);
    }

    private LinkedLearnerGrantEntity grant(UUID fromUserId, UUID toUserId) {
        return grant(fromUserId, toUserId, LinkedLearnerGrantScope.ACTIVITY);
    }

    private LinkedLearnerGrantEntity grant(
            UUID fromUserId,
            UUID toUserId,
            LinkedLearnerGrantScope scope
    ) {
        LinkedLearnerGrantEntity grant = new LinkedLearnerGrantEntity();
        grant.setId(UUID.randomUUID());
        grant.setRelationshipId(RELATIONSHIP_ID);
        grant.setFromUserId(fromUserId);
        grant.setToUserId(toUserId);
        grant.setScope(scope);
        grant.setGrantedAt(OffsetDateTime.now());
        return grant;
    }

    private UserEntity user(UUID id, Integer birthYear) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setBirthYear(birthYear);
        return user;
    }
}
