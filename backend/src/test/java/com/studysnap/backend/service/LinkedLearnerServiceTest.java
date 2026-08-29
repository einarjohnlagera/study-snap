package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.security.InvitationRateLimitService;
import com.studysnap.backend.dto.AcceptLinkedLearnerRequest;
import com.studysnap.backend.dto.InviteLinkedLearnerRequest;
import com.studysnap.backend.dto.LinkedLearnerInvitationResponse;
import com.studysnap.backend.exception.LinkedLearnerInvalidStateException;
import com.studysnap.backend.exception.LinkedLearnerBirthYearRequiredException;
import org.springframework.http.HttpStatus;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.dto.LinkedLearnerResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.LinkedLearnerGuardianConsentEntity;
import com.studysnap.backend.entity.LinkedLearnerGrantEntity;
import com.studysnap.backend.entity.LinkedLearnerGrantScope;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerSide;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.exception.LinkedLearnerNotAllowedException;
import com.studysnap.backend.exception.InvalidLinkedLearnerBirthYearException;
import com.studysnap.backend.exception.LinkedLearnerBirthYearCorrectionNotAllowedException;
import com.studysnap.backend.exception.LinkedLearnerProgressNotFoundException;
import com.studysnap.backend.exception.LinkedLearnerSelfLinkException;
import com.studysnap.backend.repository.LinkedLearnerGuardianConsentRepository;
import com.studysnap.backend.repository.LinkedLearnerGrantRepository;
import com.studysnap.backend.entity.LinkedLearnerInvitationEntity;
import static org.mockito.ArgumentMatchers.eq;
import com.studysnap.backend.repository.LinkedLearnerInvitationRepository;
import com.studysnap.backend.repository.LinkedLearnerProvisionalBirthYearRepository;
import com.studysnap.backend.repository.LinkedLearnerRelationshipRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.Year;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.ArgumentCaptor;
import java.util.Map;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class LinkedLearnerServiceTest {
    private static final String SUPPORTER_EMAIL = "supporter@example.com";
    private static final String LEARNER_EMAIL = "learner@example.com";

    @Mock private LinkedLearnerRelationshipRepository relationshipRepository;
    @Mock private LinkedLearnerInvitationRepository invitationRepository;
    @Mock private LinkedLearnerGuardianConsentRepository consentRepository;
    @Mock private LinkedLearnerGrantRepository grantRepository;
    @Mock private LinkedLearnerProvisionalBirthYearRepository provisionalBirthYearRepository;
    @Mock private UserRepository userRepository;
    @Mock private OnboardingGuardService onboardingGuardService;
    @Mock private AuthService authService;
    @Mock private EmailService emailService;
    @Mock private EmailTemplateService emailTemplateService;
    @Mock private InvitationRateLimitService invitationRateLimitService;

    /** Every relationship a test builds, so the conditional-transition stubs can act like a DB. */
    private final java.util.Map<UUID, LinkedLearnerRelationshipEntity> relationshipsById = new java.util.HashMap<>();

    private StudySnapProperties properties;
    private LinkedLearnerService service;

    @BeforeEach
    void setUp() {
        properties = new StudySnapProperties();
        service = new LinkedLearnerService(
                relationshipRepository,
                invitationRepository,
                consentRepository,
                grantRepository,
                provisionalBirthYearRepository,
                userRepository,
                onboardingGuardService,
                authService,
                emailService,
                emailTemplateService,
                properties,
                new GuardianConsentPolicy(properties),
                invitationRateLimitService
        );
        // The birth-year decision loads the learner through a PESSIMISTIC_WRITE read. Route it to
        // whatever findById is stubbed with, so a test cannot stub only one of the two and appear
        // to exercise a path it never reaches.
        lenient().when(userRepository.findByIdForUpdate(any(UUID.class)))
                .thenAnswer(invocation -> userRepository.findById(invocation.getArgument(0)));
        // The consent decision reads the birth year as a SCALAR, never off the entity — see
        // UserRepository.findBirthYearById for why. Route it to the same fixture so a test that
        // sets a birth year on its user still exercises the real path.
        lenient().when(userRepository.findBirthYearById(any(UUID.class)))
                .thenAnswer(invocation -> userRepository.findById(invocation.<UUID>getArgument(0))
                        .map(UserEntity::getBirthYear));
        lenient().when(provisionalBirthYearRepository.findEffectiveBirthYear(any(), any()))
                .thenAnswer(invocation -> userRepository.findById(invocation.<UUID>getArgument(1))
                        .map(UserEntity::getBirthYear));
        lenient().when(userRepository.writeBirthYear(any(UUID.class), any(), any()))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    return userRepository.findById(id).map(user -> {
                        user.setBirthYear(invocation.getArgument(1));
                        user.setBirthYearUpdatedAt(invocation.getArgument(2));
                        user.setUpdatedAt(invocation.getArgument(2));
                        return 1;
                    }).orElse(0);
                });

        // ⚠️ Model the CONDITIONAL transitions like the database: apply the guard, mutate on
        // success, report rows affected. A mock that always returns 0 and never mutates would make
        // every final-status assertion below assert the fixture instead of the code.
        //
        // ⚠️ This models transitions ONLY — not isolation, locking, or concurrent transactions.
        // Those are proven in LinkedLearnerConcurrencyTest against a real transaction manager,
        // because a Mockito stub cannot prove them and must not be mistaken for proof.
        lenient().when(relationshipRepository.markAcceptedIfPending(any(UUID.class), any()))
                .thenAnswer(invocation -> transition(invocation.getArgument(0),
                        LinkedLearnerStatus.PENDING, LinkedLearnerStatus.ACCEPTED, invocation.getArgument(1)));
        lenient().when(relationshipRepository.markRevokedIfLive(any(UUID.class), any()))
                .thenAnswer(invocation -> {
                    LinkedLearnerRelationshipEntity row = relationshipsById.get(invocation.getArgument(0));
                    if (row == null || row.getStatus() == LinkedLearnerStatus.REVOKED) {
                        return 0;
                    }
                    row.setStatus(LinkedLearnerStatus.REVOKED);
                    row.setRevokedAt(invocation.getArgument(1));
                    return 1;
                });
        lenient().when(relationshipRepository.pauseAcceptedForConsent(any(UUID.class)))
                .thenAnswer(invocation -> transition(invocation.getArgument(0),
                        LinkedLearnerStatus.ACCEPTED, LinkedLearnerStatus.PENDING, null));
        lenient().when(relationshipRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(relationshipsById.get(invocation.getArgument(0))));
    }

    private int transition(UUID id, LinkedLearnerStatus from, LinkedLearnerStatus to, OffsetDateTime acceptedAt) {
        LinkedLearnerRelationshipEntity row = relationshipsById.get(id);
        if (row == null || row.getStatus() != from) {
            return 0;
        }
        row.setStatus(to);
        row.setAcceptedAt(to == LinkedLearnerStatus.ACCEPTED ? acceptedAt : null);
        if (to == LinkedLearnerStatus.ACCEPTED) {
            row.setRevokedAt(null);
        }
        return 1;
    }

    @Test
    void explicitAcceptanceByInvitedLearnerPersistsAcceptedStatus() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(2000);
        LinkedLearnerRelationshipEntity relationship = relationship(supporter, learner, LinkedLearnerSide.SUPPORTER);
        stubRelationshipUsers(relationship, supporter, learner);
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());

        LinkedLearnerResponse response = service.accept(
                relationship.getId(), learner.getId(), new AcceptLinkedLearnerRequest(null, false));

        assertThat(response.status()).isEqualTo(LinkedLearnerStatus.ACCEPTED);
        assertThat(relationship.getStatus()).isEqualTo(LinkedLearnerStatus.ACCEPTED);
        assertThat(relationship.getAcceptedAt()).isNotNull();
        verify(relationshipRepository).markAcceptedIfPending(eq(relationship.getId()), any());
    }

    @Test
    void linkRedeemerCannotAcceptAndTheLinkCreatorConfirmationActivatesTheRelationship() {
        UserEntity creatorSupporter = user(SUPPORTER_EMAIL);
        UserEntity redeemerLearner = user(LEARNER_EMAIL);
        redeemerLearner.setBirthYear(2000);
        // Link redemption makes the redeemer the initiator. The creator is therefore the existing
        // acceptance machinery's invited party, preserving two distinct acts of agreement.
        LinkedLearnerRelationshipEntity relationship =
                relationship(creatorSupporter, redeemerLearner, LinkedLearnerSide.LEARNER);
        stubRelationshipUsers(relationship, creatorSupporter, redeemerLearner);
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());
        AcceptLinkedLearnerRequest request = new AcceptLinkedLearnerRequest(null, false);

        assertThatThrownBy(() -> service.accept(relationship.getId(), redeemerLearner.getId(), request))
                .isInstanceOf(LinkedLearnerNotAllowedException.class);

        LinkedLearnerResponse confirmed = service.accept(
                relationship.getId(), creatorSupporter.getId(), request);

        assertThat(confirmed.status()).isEqualTo(LinkedLearnerStatus.ACCEPTED);
        assertThat(relationship.getStatus()).isEqualTo(LinkedLearnerStatus.ACCEPTED);
    }

    @Test
    void minorLinkRedeemerStaysPendingUntilTheCreatorRecordsGuardianConsent() {
        UserEntity creatorSupporter = user(SUPPORTER_EMAIL);
        UserEntity redeemerLearner = user(LEARNER_EMAIL);
        redeemerLearner.setBirthYear(Year.now().getValue() - 10);
        LinkedLearnerRelationshipEntity relationship =
                relationship(creatorSupporter, redeemerLearner, LinkedLearnerSide.LEARNER);
        stubRelationshipUsers(relationship, creatorSupporter, redeemerLearner);
        when(consentRepository.findByRelationshipId(relationship.getId()))
                .thenReturn(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(new LinkedLearnerGuardianConsentEntity()));

        LinkedLearnerResponse withoutConsent = service.accept(
                relationship.getId(), creatorSupporter.getId(), new AcceptLinkedLearnerRequest(null, false));

        assertThat(withoutConsent.status()).isEqualTo(LinkedLearnerStatus.PENDING);
        assertThat(withoutConsent.counterpartyEmail()).isNull();
        assertThat(relationship.getStatus()).isEqualTo(LinkedLearnerStatus.PENDING);

        LinkedLearnerResponse withConsent = service.accept(
                relationship.getId(), creatorSupporter.getId(), new AcceptLinkedLearnerRequest(null, true));

        assertThat(withConsent.status()).isEqualTo(LinkedLearnerStatus.ACCEPTED);
        verify(consentRepository).save(any(LinkedLearnerGuardianConsentEntity.class));
    }

    @Test
    void provisionalMinorYearKeepsGuardianConsentReachableAndPromotesOnlyOnAcceptance() {
        UserEntity creatorSupporter = user(SUPPORTER_EMAIL);
        UserEntity redeemerLearner = user(LEARNER_EMAIL);
        redeemerLearner.setBirthYear(null);
        int provisionalYear = Year.now().getValue() - 10;
        LinkedLearnerRelationshipEntity relationship =
                relationship(creatorSupporter, redeemerLearner, LinkedLearnerSide.LEARNER);
        LinkedLearnerGuardianConsentEntity consent = new LinkedLearnerGuardianConsentEntity();
        consent.setRelationshipId(relationship.getId());
        stubRelationshipUsers(relationship, creatorSupporter, redeemerLearner);
        when(provisionalBirthYearRepository.findEffectiveBirthYear(
                relationship.getId(), redeemerLearner.getId())).thenReturn(Optional.of(provisionalYear));
        when(consentRepository.findByRelationshipId(relationship.getId()))
                .thenReturn(Optional.empty(), Optional.of(consent), Optional.of(consent), Optional.of(consent));
        when(consentRepository.save(any())).thenReturn(consent);

        LinkedLearnerResponse consented = service.recordGuardianConsent(
                relationship.getId(), creatorSupporter.getId());
        assertThat(consented.guardianConsentRecorded()).isTrue();
        assertThat(relationship.getStatus()).isEqualTo(LinkedLearnerStatus.PENDING);
        verify(userRepository, never()).writeBirthYear(any(), any(), any());
        verify(provisionalBirthYearRepository, never())
                .promoteIfAccountBirthYearMissing(any(), any(), any());

        LinkedLearnerResponse accepted = service.accept(
                relationship.getId(), creatorSupporter.getId(),
                new AcceptLinkedLearnerRequest(null, false));

        assertThat(accepted.status()).isEqualTo(LinkedLearnerStatus.ACCEPTED);
        verify(provisionalBirthYearRepository).promoteIfAccountBirthYearMissing(
                eq(relationship.getId()), eq(redeemerLearner.getId()), any());
        verify(provisionalBirthYearRepository).deleteForRelationship(relationship.getId());
    }

    @Test
    void bothPartiesRefreshAProvisionalMinorAsPendingAndConsentRequired() {
        UserEntity creatorSupporter = user(SUPPORTER_EMAIL);
        UserEntity redeemerLearner = user(LEARNER_EMAIL);
        redeemerLearner.setBirthYear(null);
        LinkedLearnerRelationshipEntity relationship =
                relationship(creatorSupporter, redeemerLearner, LinkedLearnerSide.LEARNER);
        stubRelationshipUsers(relationship, creatorSupporter, redeemerLearner);
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                creatorSupporter.getId(), creatorSupporter.getId())).thenReturn(List.of(relationship));
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                redeemerLearner.getId(), redeemerLearner.getId())).thenReturn(List.of(relationship));
        when(provisionalBirthYearRepository.findEffectiveBirthYear(
                relationship.getId(), redeemerLearner.getId()))
                .thenReturn(Optional.of(Year.now().getValue() - 10));
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());

        LinkedLearnerResponse creatorView = service.list(creatorSupporter.getId()).getFirst();
        LinkedLearnerResponse redeemerView = service.list(redeemerLearner.getId()).getFirst();

        assertThat(creatorView.status()).isEqualTo(LinkedLearnerStatus.PENDING);
        assertThat(creatorView.incomingInvitation()).isTrue();
        assertThat(creatorView.birthYearRequired()).isFalse();
        assertThat(creatorView.guardianConsentRequired()).isTrue();
        assertThat(creatorView.activitySharedWithMe()).isFalse();
        assertThat(creatorView.progressSharedWithMe()).isFalse();
        assertThat(redeemerView.status()).isEqualTo(LinkedLearnerStatus.PENDING);
        assertThat(redeemerView.incomingInvitation()).isFalse();
        assertThat(redeemerView.birthYearRequired()).isFalse();
        assertThat(redeemerView.guardianConsentRequired()).isTrue();
    }

    @Test
    void redemptionPreparationValidatesBeforeLockAndSkipsProvisioningForAnExistingYear() {
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(2000);
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));

        assertThat(service.prepareProvisionalBirthYearForLinkRedemption(learner.getId(), null)).isNull();
        assertThatThrownBy(() -> service.prepareProvisionalBirthYearForLinkRedemption(
                learner.getId(), Year.now().getValue() + 1))
                .isInstanceOf(InvalidLinkedLearnerBirthYearException.class);
    }

    @Test
    void redemptionPreparationRequiresADeclarationWhenTheAccountYearIsMissing() {
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(null);
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));

        assertThatThrownBy(() -> service.prepareProvisionalBirthYearForLinkRedemption(
                learner.getId(), null))
                .isInstanceOf(LinkedLearnerBirthYearRequiredException.class);
    }

    @Test
    void unknownAndKnownEmailInvitesAreIndistinguishableInResponseAndInState() {
        UserEntity caller = user("caller@example.com");
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        when(invitationRepository.findFirstByInviterUserIdAndInvitedEmailAndStatus(
                any(UUID.class), anyString(), eq(LinkedLearnerStatus.PENDING)))
                .thenReturn(Optional.of(invitation(caller.getId(), "x@example.com")));
        when(emailTemplateService.render(anyString(), anyMap())).thenReturn(
                new EmailTemplateService.RenderedEmailTemplate("Subject", "HTML", "Text"));

        SimpleMessageResponse unknown = service.invite(
                caller.getId(), new InviteLinkedLearnerRequest("unknown@example.com", LinkedLearnerSide.SUPPORTER, null));
        SimpleMessageResponse known = service.invite(
                caller.getId(), new InviteLinkedLearnerRequest("known@example.com", LinkedLearnerSide.SUPPORTER, null));

        assertThat(known).isEqualTo(unknown);
        // ⚠️ THIS is the assertion that matters, and the one the previous version could not make.
        // Comparing responses compares references to a shared constant and cannot fail. The
        // observable difference an attacker used was STATE: a real address wrote a row, an unknown
        // one wrote nothing. Now BOTH write one, so there is no branch on existence to observe.
        verify(invitationRepository, times(2)).insertPendingIfAbsent(
                any(UUID.class), eq(caller.getId()), anyString(), anyString(),
                any(OffsetDateTime.class), any(OffsetDateTime.class));
        // And the account table is never consulted on this path at all.
        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void inviterNameIsStrippedOfMarkupBeforeItReachesOutboundEmail() {
        // The invitation is the only email putting one user's self-chosen text in front of ANOTHER
        // user, and the template engine does not escape. Unstripped, this delivers attacker-authored
        // HTML from NoteLib's own signed sending domain.
        UserEntity caller = user("caller@example.com");
        caller.setDisplayName("<a href=\"https://evil.example\">Confirm your NoteLib account</a>");
        UserEntity counterparty = user("known@example.com");
        LinkedLearnerRelationshipEntity pending =
                relationship(caller, counterparty, LinkedLearnerSide.SUPPORTER);
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        when(invitationRepository.findFirstByInviterUserIdAndInvitedEmailAndStatus(
                any(UUID.class), anyString(), eq(LinkedLearnerStatus.PENDING)))
                .thenReturn(Optional.of(invitation(caller.getId(), "known@example.com")));
        when(emailTemplateService.render(anyString(), anyMap())).thenReturn(
                new EmailTemplateService.RenderedEmailTemplate("Subject", "HTML", "Text"));

        service.invite(caller.getId(),
                new InviteLinkedLearnerRequest("known@example.com", LinkedLearnerSide.SUPPORTER, null));

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(emailTemplateService).render(anyString(), captor.capture());
        assertThat(captor.getValue().get("inviterName")).doesNotContain("<").doesNotContain(">");
    }

    @Test
    void inviteRequiresEmailVerificationBeforeMailingAThirdParty() {
        UUID callerUserId = UUID.randomUUID();
        doThrow(new AppException("EMAIL_NOT_VERIFIED", "Verify your email before using this feature.",
                org.springframework.http.HttpStatus.FORBIDDEN))
                .when(authService).requireEmailVerified(callerUserId);

        assertThatThrownBy(() -> service.invite(
                callerUserId, new InviteLinkedLearnerRequest("someone@example.com", LinkedLearnerSide.SUPPORTER, null)))
                .isInstanceOf(AppException.class);

        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
        verify(relationshipRepository, never()).insertPendingIfAbsent(
                any(UUID.class), any(UUID.class), any(UUID.class), anyString(), any(OffsetDateTime.class));
    }

    @Test
    void onlyInvitedPartyCanAccept() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(2000);
        LinkedLearnerRelationshipEntity relationship = relationship(supporter, learner, LinkedLearnerSide.SUPPORTER);
        when(relationshipRepository.findById(relationship.getId())).thenReturn(Optional.of(relationship));

        assertThatThrownBy(() -> service.accept(
                relationship.getId(), supporter.getId(), new AcceptLinkedLearnerRequest(null, false)))
                .isInstanceOf(LinkedLearnerNotAllowedException.class);
        assertThat(relationship.getStatus()).isEqualTo(LinkedLearnerStatus.PENDING);
        verify(relationshipRepository, never()).markAcceptedIfPending(any(UUID.class), any());
    }

    @Test
    void thirdPartyCannotAccept() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        LinkedLearnerRelationshipEntity relationship = relationship(supporter, learner, LinkedLearnerSide.SUPPORTER);
        when(relationshipRepository.findById(relationship.getId())).thenReturn(Optional.of(relationship));
        UUID thirdPartyId = UUID.randomUUID();

        assertThatThrownBy(() -> service.accept(
                relationship.getId(), thirdPartyId, new AcceptLinkedLearnerRequest(null, false)))
                .isInstanceOf(LinkedLearnerNotAllowedException.class);
        assertThat(relationship.getStatus()).isEqualTo(LinkedLearnerStatus.PENDING);
    }

    @Test
    void selfLinkingIsRefusedOnTheAddressBeforeAnyAccountLookup() {
        UserEntity caller = user("same@example.com");
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        InviteLinkedLearnerRequest request = new InviteLinkedLearnerRequest(
                "same@example.com", LinkedLearnerSide.SUPPORTER, null);

        assertThatThrownBy(() -> service.invite(caller.getId(), request))
                .isInstanceOf(LinkedLearnerSelfLinkException.class);

        // ⚠️ Refused WITHOUT consulting the account table. The caller already knows their own
        // address exists, so this reveals nothing — but deciding it after a lookup would make the
        // refusal itself depend on account state, which is the property being protected.
        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
        verify(invitationRepository, never()).insertPendingIfAbsent(any(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    void eitherPartyCanRevokePendingAndRevocationIsIdempotent() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        LinkedLearnerRelationshipEntity relationship = relationship(supporter, learner, LinkedLearnerSide.SUPPORTER);
        stubRelationshipUsers(relationship, supporter, learner);
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());

        LinkedLearnerResponse revoked = service.revoke(relationship.getId(), learner.getId());
        LinkedLearnerResponse repeated = service.revoke(relationship.getId(), supporter.getId());

        assertThat(revoked.status()).isEqualTo(LinkedLearnerStatus.REVOKED);
        assertThat(repeated.status()).isEqualTo(LinkedLearnerStatus.REVOKED);
        verify(relationshipRepository).markRevokedIfLive(eq(relationship.getId()), any());
    }

    @Test
    void supporterCanRevokeAcceptedRelationship() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(2000);
        LinkedLearnerRelationshipEntity relationship = relationship(supporter, learner, LinkedLearnerSide.LEARNER);
        relationship.setStatus(LinkedLearnerStatus.ACCEPTED);
        relationship.setAcceptedAt(OffsetDateTime.now().minusDays(1));
        when(relationshipRepository.findById(relationship.getId())).thenReturn(Optional.of(relationship));
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());

        LinkedLearnerResponse response = service.revoke(relationship.getId(), supporter.getId());

        assertThat(response.status()).isEqualTo(LinkedLearnerStatus.REVOKED);
        assertThat(relationship.getRevokedAt()).isNotNull();
    }

    @Test
    void minorCannotReachAcceptedWithoutPersistedConsent() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(Year.now().getValue() - 10);
        LinkedLearnerRelationshipEntity relationship = relationship(supporter, learner, LinkedLearnerSide.SUPPORTER);
        stubRelationshipUsers(relationship, supporter, learner);
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());

        LinkedLearnerResponse response = service.accept(
                relationship.getId(), learner.getId(), new AcceptLinkedLearnerRequest(null, false));

        assertThat(response.status()).isEqualTo(LinkedLearnerStatus.PENDING);
        assertThat(response.guardianConsentRequired()).isTrue();
        assertThat(response.guardianConsentRecorded()).isFalse();
        verify(relationshipRepository, never()).markAcceptedIfPending(any(UUID.class), any());
    }

    @Test
    void minorReachesAcceptedAfterConsentIsPersisted() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(Year.now().getValue() - 10);
        LinkedLearnerRelationshipEntity relationship = relationship(supporter, learner, LinkedLearnerSide.SUPPORTER);
        LinkedLearnerGuardianConsentEntity consent = new LinkedLearnerGuardianConsentEntity();
        consent.setRelationshipId(relationship.getId());
        stubRelationshipUsers(relationship, supporter, learner);
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.of(consent));

        LinkedLearnerResponse response = service.accept(
                relationship.getId(), learner.getId(), new AcceptLinkedLearnerRequest(null, false));

        assertThat(response.status()).isEqualTo(LinkedLearnerStatus.ACCEPTED);
        assertThat(relationship.getStatus()).isEqualTo(LinkedLearnerStatus.ACCEPTED);
        verify(relationshipRepository).markAcceptedIfPending(eq(relationship.getId()), any());
    }

    @Test
    void changingConfiguredThresholdChangesConsentOutcome() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(Year.now().getValue() - 20);
        LinkedLearnerRelationshipEntity relationship = relationship(supporter, learner, LinkedLearnerSide.SUPPORTER);
        stubRelationshipUsers(relationship, supporter, learner);
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());
        properties.getLinkedLearners().setGuardianConsentMaxAge(17);

        LinkedLearnerResponse accepted = service.accept(
                relationship.getId(), learner.getId(), new AcceptLinkedLearnerRequest(null, false));
        assertThat(accepted.status()).isEqualTo(LinkedLearnerStatus.ACCEPTED);

        relationship.setStatus(LinkedLearnerStatus.PENDING);
        relationship.setAcceptedAt(null);
        properties.getLinkedLearners().setGuardianConsentMaxAge(25);
        LinkedLearnerResponse pending = service.accept(
                relationship.getId(), learner.getId(), new AcceptLinkedLearnerRequest(null, false));
        assertThat(pending.status()).isEqualTo(LinkedLearnerStatus.PENDING);
    }

    @Test
    void guardianConsentIsStoredAsDistinctFactWithAttestorAndTime() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(Year.now().getValue() - 10);
        LinkedLearnerRelationshipEntity relationship = relationship(supporter, learner, LinkedLearnerSide.LEARNER);
        when(relationshipRepository.findById(relationship.getId())).thenReturn(Optional.of(relationship));
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());
        when(consentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.recordGuardianConsent(relationship.getId(), supporter.getId());

        var captor = org.mockito.ArgumentCaptor.forClass(LinkedLearnerGuardianConsentEntity.class);
        verify(consentRepository).save(captor.capture());
        assertThat(captor.getValue().getAttestedByUserId()).isEqualTo(supporter.getId());
        assertThat(captor.getValue().getLearnerUserId()).isEqualTo(learner.getId());
        assertThat(captor.getValue().getAttestedAt()).isNotNull();
    }

    @Test
    void downwardCorrectionPausesUnconsentedAcceptedRelationshipAndClearsAcceptedAt() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(Year.now().getValue() - 30);
        LinkedLearnerRelationshipEntity relationship = acceptedRelationship(supporter, learner);
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));
        when(relationshipRepository.findByLearnerUserIdAndStatus(
                learner.getId(), LinkedLearnerStatus.ACCEPTED)).thenReturn(List.of(relationship));
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                learner.getId(), learner.getId())).thenReturn(List.of());

        service.correctBirthYear(learner.getId(), Year.now().getValue() - 10);

        assertThat(relationship.getStatus()).isEqualTo(LinkedLearnerStatus.PENDING);
        assertThat(relationship.getAcceptedAt()).isNull();
        assertThat(learner.getBirthYearUpdatedAt()).isNotNull();
        verify(relationshipRepository).pauseAcceptedForConsent(relationship.getId());
    }

    @Test
    void correctionPreviewCountsOnlyAcceptedRelationshipsWithoutConsent() {
        UserEntity firstSupporter = user(SUPPORTER_EMAIL);
        UserEntity secondSupporter = user("second-supporter@example.com");
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(Year.now().getValue() - 30);
        LinkedLearnerRelationshipEntity unconsented = acceptedRelationship(firstSupporter, learner);
        LinkedLearnerRelationshipEntity consented = acceptedRelationship(secondSupporter, learner);
        LinkedLearnerGuardianConsentEntity consent = new LinkedLearnerGuardianConsentEntity();
        consent.setRelationshipId(consented.getId());
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));
        when(relationshipRepository.findByLearnerUserIdAndStatus(
                learner.getId(), LinkedLearnerStatus.ACCEPTED)).thenReturn(List.of(unconsented, consented));
        when(consentRepository.findByRelationshipId(unconsented.getId())).thenReturn(Optional.empty());
        when(consentRepository.findByRelationshipId(consented.getId())).thenReturn(Optional.of(consent));

        var preview = service.previewBirthYearCorrection(
                learner.getId(), Year.now().getValue() - 10);

        assertThat(preview.affectedConnectionCount()).isOne();
        verify(userRepository, never()).save(any());
        verify(relationshipRepository, never()).pauseAcceptedForConsent(any(UUID.class));
    }

    @Test
    void downwardCorrectionLeavesConsentedAcceptedRelationshipAccepted() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(Year.now().getValue() - 30);
        LinkedLearnerRelationshipEntity relationship = acceptedRelationship(supporter, learner);
        LinkedLearnerGuardianConsentEntity consent = new LinkedLearnerGuardianConsentEntity();
        consent.setRelationshipId(relationship.getId());
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));
        when(relationshipRepository.findByLearnerUserIdAndStatus(
                learner.getId(), LinkedLearnerStatus.ACCEPTED)).thenReturn(List.of(relationship));
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.of(consent));
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                learner.getId(), learner.getId())).thenReturn(List.of());

        service.correctBirthYear(learner.getId(), Year.now().getValue() - 10);

        assertThat(relationship.getStatus()).isEqualTo(LinkedLearnerStatus.ACCEPTED);
        assertThat(relationship.getAcceptedAt()).isNotNull();
        verify(relationshipRepository, never()).pauseAcceptedForConsent(any(UUID.class));
    }

    @Test
    void downwardCorrectionLeavesPendingAndRevokedRelationshipsUntouched() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(Year.now().getValue() - 30);
        LinkedLearnerRelationshipEntity pending = relationship(supporter, learner, LinkedLearnerSide.SUPPORTER);
        LinkedLearnerRelationshipEntity revoked = relationship(supporter, learner, LinkedLearnerSide.LEARNER);
        revoked.setStatus(LinkedLearnerStatus.REVOKED);
        OffsetDateTime revokedAt = OffsetDateTime.now().minusDays(1);
        revoked.setRevokedAt(revokedAt);
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));
        when(relationshipRepository.findByLearnerUserIdAndStatus(
                learner.getId(), LinkedLearnerStatus.ACCEPTED)).thenReturn(List.of());
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                learner.getId(), learner.getId())).thenReturn(List.of());

        service.correctBirthYear(learner.getId(), Year.now().getValue() - 10);

        assertThat(pending.getStatus()).isEqualTo(LinkedLearnerStatus.PENDING);
        assertThat(revoked.getStatus()).isEqualTo(LinkedLearnerStatus.REVOKED);
        assertThat(revoked.getRevokedAt()).isEqualTo(revokedAt);
    }

    @Test
    void upwardCorrectionChangesNoRelationship() {
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(Year.now().getValue() - 10);
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                learner.getId(), learner.getId())).thenReturn(List.of());

        service.correctBirthYear(learner.getId(), Year.now().getValue() - 30);

        verify(relationshipRepository, never()).findByLearnerUserIdAndStatus(any(), any());
        verify(relationshipRepository, never()).pauseAcceptedForConsent(any(UUID.class));
    }

    @Test
    void noOpCorrectionDoesNotTouchTimestampOrRelationships() {
        UserEntity learner = user(LEARNER_EMAIL);
        int birthYear = Year.now().getValue() - 20;
        learner.setBirthYear(birthYear);
        OffsetDateTime originalTimestamp = OffsetDateTime.now().minusDays(2);
        learner.setBirthYearUpdatedAt(originalTimestamp);
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                learner.getId(), learner.getId())).thenReturn(List.of());

        service.correctBirthYear(learner.getId(), birthYear);

        assertThat(learner.getBirthYearUpdatedAt()).isEqualTo(originalTimestamp);
        verify(userRepository, never()).save(any());
        verify(relationshipRepository, never()).findByLearnerUserIdAndStatus(any(), any());
        verify(relationshipRepository, never()).pauseAcceptedForConsent(any(UUID.class));
    }

    @Test
    void correctionUsesTheDatabasePlausibilityBounds() {
        UUID learnerUserId = UUID.randomUUID();

        assertThatThrownBy(() -> service.correctBirthYear(learnerUserId, 1899))
                .isInstanceOf(InvalidLinkedLearnerBirthYearException.class);
        assertThatThrownBy(() -> service.correctBirthYear(learnerUserId, 10000))
                .isInstanceOf(InvalidLinkedLearnerBirthYearException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void learnerWithNoCurrentLinksCanCorrectTheirOwnBirthYear() {
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(2000);
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                learner.getId(), learner.getId())).thenReturn(List.of());

        service.correctBirthYear(learner.getId(), 2001);

        assertThat(learner.getBirthYear()).isEqualTo(2001);
        assertThat(learner.getBirthYearUpdatedAt()).isNotNull();
    }

    @Test
    void correctionCannotBecomeAFirstDeclarationOutsideTheLinkFlow() {
        UserEntity learner = user(LEARNER_EMAIL);
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));

        assertThatThrownBy(() -> service.correctBirthYear(learner.getId(), 2001))
                .isInstanceOf(LinkedLearnerBirthYearCorrectionNotAllowedException.class);

        assertThat(learner.getBirthYear()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    void downwardCorrectionCutsSupporterProgressReadOnTheNextAuthorizationCheck() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(Year.now().getValue() - 30);
        LinkedLearnerRelationshipEntity relationship = acceptedRelationship(supporter, learner);
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));
        when(relationshipRepository.findById(relationship.getId())).thenReturn(Optional.of(relationship));
        when(relationshipRepository.findByLearnerUserIdAndStatus(
                learner.getId(), LinkedLearnerStatus.ACCEPTED)).thenReturn(List.of(relationship));
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                learner.getId(), learner.getId())).thenReturn(List.of());
        LinkedLearnerReadAuthorizationService readAuthorizationService =
                new LinkedLearnerReadAuthorizationService(
                        relationshipRepository,
                        progressGrantAuthorization(relationship));

        UUID authorizedLearnerId = readAuthorizationService.requireAcceptedLearnerId(
                supporter.getId(), relationship.getId());
        service.correctBirthYear(learner.getId(), Year.now().getValue() - 10);

        assertThat(authorizedLearnerId).isEqualTo(learner.getId());
        assertThatThrownBy(() -> readAuthorizationService.requireAcceptedLearnerId(
                supporter.getId(), relationship.getId()))
                .isInstanceOf(LinkedLearnerProgressNotFoundException.class);
    }

    private LinkedLearnerGrantAuthorizationService progressGrantAuthorization(
            LinkedLearnerRelationshipEntity relationship
    ) {
        LinkedLearnerGrantAuthorizationService authorizationService =
                mock(LinkedLearnerGrantAuthorizationService.class);
        when(authorizationService.requireGrant(
                relationship.getSupporterUserId(), relationship.getId(), LinkedLearnerGrantScope.PROGRESS))
                .thenAnswer(invocation -> {
                    if (relationship.getStatus() != LinkedLearnerStatus.ACCEPTED) {
                        throw new com.studysnap.backend.exception.LinkedLearnerNotFoundException();
                    }
                    return relationship.getLearnerUserId();
                });
        return authorizationService;
    }

    @Test
    void failedRelationshipWritePropagatesSoTheTransactionalCorrectionRollsBack() throws NoSuchMethodException {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(Year.now().getValue() - 30);
        LinkedLearnerRelationshipEntity relationship = acceptedRelationship(supporter, learner);
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));
        when(relationshipRepository.findByLearnerUserIdAndStatus(
                learner.getId(), LinkedLearnerStatus.ACCEPTED)).thenReturn(List.of(relationship));
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("forced relationship write failure"))
                .when(relationshipRepository).pauseAcceptedForConsent(any(UUID.class));

        assertThatThrownBy(() -> service.correctBirthYear(
                learner.getId(), Year.now().getValue() - 10))
                .isInstanceOf(IllegalStateException.class);
        assertThat(LinkedLearnerService.class
                .getMethod("correctBirthYear", UUID.class, int.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    /**
     * ⚠️ REGRESSION GUARD FOR A LOCK STORM, not a correctness assertion about the payload.
     *
     * <p>{@code toResponse} runs once per relationship inside {@code list()}. Routing its
     * birth-year lookup through {@code resolveEffectiveBirthYearForDecision} — which calls
     * {@code lockAndReadBirthYear}, a PESSIMISTIC_WRITE lock — makes a plain connection list take a
     * row-level write lock on EVERY counterparty, in list order. That breaks the one-row invariant
     * {@code lockAndReadBirthYear}'s own Javadoc states and lets two concurrent listers with
     * overlapping learners deadlock.
     *
     * <p>This shipped once in this release and was caught at review. Nothing pinned it: reverting
     * the fix left the entire service and concurrency suite green, because every repository here is
     * a mock and a mock cannot observe a lock. Assert the CALL instead.
     */
    @Test
    void listTakesNoRowLockOnAnyCounterparty() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        LinkedLearnerRelationshipEntity relationship =
                relationship(supporter, learner, LinkedLearnerSide.SUPPORTER);
        relationship.setStatus(LinkedLearnerStatus.ACCEPTED);
        stubRelationshipUsers(relationship, supporter, learner);
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                supporter.getId(), supporter.getId())).thenReturn(List.of(relationship));
        when(consentRepository.findByRelationshipId(relationship.getId()))
                .thenReturn(Optional.empty());

        service.list(supporter.getId());

        verify(userRepository, never()).findByIdForUpdate(any(UUID.class));
    }

    @Test
    void listResponseExposesPermissionFlagsButNoLearnerProgressPayload() {
        Set<String> componentNames = Arrays.stream(LinkedLearnerResponse.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .collect(Collectors.toSet());

        assertThat(componentNames).noneMatch(name ->
                name.contains("readiness")
                        || name.contains("score")
                        || name.contains("quiz")
                        || name.contains("note")
                        || name.contains("studypack")
                        || name.contains("concepthealth"));
    }

    @Test
    void listComputesBothScopesAndDirectionsIndependentlyForBothCallers() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(Year.now().getValue() - 30);
        LinkedLearnerRelationshipEntity relationship = acceptedRelationship(supporter, learner);
        LinkedLearnerGrantEntity learnerToSupporter = new LinkedLearnerGrantEntity();
        learnerToSupporter.setId(UUID.randomUUID());
        learnerToSupporter.setRelationshipId(relationship.getId());
        learnerToSupporter.setFromUserId(learner.getId());
        learnerToSupporter.setToUserId(supporter.getId());
        learnerToSupporter.setScope(LinkedLearnerGrantScope.ACTIVITY);
        learnerToSupporter.setGrantedAt(OffsetDateTime.now());
        LinkedLearnerGrantEntity learnerProgressToSupporter = new LinkedLearnerGrantEntity();
        learnerProgressToSupporter.setId(UUID.randomUUID());
        learnerProgressToSupporter.setRelationshipId(relationship.getId());
        learnerProgressToSupporter.setFromUserId(learner.getId());
        learnerProgressToSupporter.setToUserId(supporter.getId());
        learnerProgressToSupporter.setScope(LinkedLearnerGrantScope.PROGRESS);
        learnerProgressToSupporter.setGrantedAt(OffsetDateTime.now());
        stubUser(supporter);
        stubUser(learner);
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                learner.getId(), learner.getId())).thenReturn(List.of(relationship));
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                supporter.getId(), supporter.getId())).thenReturn(List.of(relationship));
        when(grantRepository.findByRelationshipIdInAndScopeInAndRevokedAtIsNull(
                Set.of(relationship.getId()), List.of(
                        LinkedLearnerGrantScope.ACTIVITY, LinkedLearnerGrantScope.PROGRESS)))
                .thenReturn(List.of(learnerToSupporter, learnerProgressToSupporter));

        LinkedLearnerResponse supporterView = service.list(supporter.getId()).getFirst();
        LinkedLearnerResponse learnerView = service.list(learner.getId()).getFirst();

        assertThat(supporterView.activitySharedByMe()).isFalse();
        assertThat(supporterView.activitySharedWithMe()).isTrue();
        assertThat(supporterView.progressSharedByMe()).isFalse();
        assertThat(supporterView.progressSharedWithMe()).isTrue();
        assertThat(learnerView.activitySharedByMe()).isTrue();
        assertThat(learnerView.activitySharedWithMe()).isFalse();
        assertThat(learnerView.progressSharedByMe()).isTrue();
        assertThat(learnerView.progressSharedWithMe()).isFalse();
        verify(grantRepository, times(2)).findByRelationshipIdInAndScopeInAndRevokedAtIsNull(
                Set.of(relationship.getId()), List.of(
                        LinkedLearnerGrantScope.ACTIVITY, LinkedLearnerGrantScope.PROGRESS));
    }

    /**
     * ⚠️ The DTO must not be MORE PERMISSIVE than {@code requireGrant}, which denies on missing
     * guardian consent. Before this, `*SharedWithMe` was `accepted && grantExists` only, so a
     * supporter could be shown a "View progress" link whose read then 404s — with no way back,
     * because recordGuardianConsent requires PENDING. Reachable by raising GUARDIAN_CONSENT_MAX_AGE,
     * which is owner-owned and pending counsel.
     */
    /**
     * ⚠️ The DTO must fail CLOSED on an unknown learner age, matching {@code requireGrant}'s
     * deny-on-null branch. `consentRequired` is false when the year is null, so without an explicit
     * unknown-age check the DTO showed access the authorization call denies — the stale-permissive
     * shape item 5 existed to remove, present in item 5's own code. Found by the v0.94.0 pressure test.
     */
    @Test
    void unknownLearnerAgeWithholdsAccessJustAsRequireGrantDenies() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(null);
        LinkedLearnerRelationshipEntity relationship = acceptedRelationship(supporter, learner);
        LinkedLearnerGrantEntity activity = grant(
                relationship, learner.getId(), supporter.getId(), LinkedLearnerGrantScope.ACTIVITY);
        LinkedLearnerGrantEntity progress = grant(
                relationship, learner.getId(), supporter.getId(), LinkedLearnerGrantScope.PROGRESS);
        stubUser(supporter);
        stubUser(learner);
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                supporter.getId(), supporter.getId())).thenReturn(List.of(relationship));
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());
        when(grantRepository.findByRelationshipIdInAndScopeInAndRevokedAtIsNull(
                Set.of(relationship.getId()), List.of(
                        LinkedLearnerGrantScope.ACTIVITY, LinkedLearnerGrantScope.PROGRESS)))
                .thenReturn(List.of(activity, progress));

        LinkedLearnerResponse supporterView = service.list(supporter.getId()).getFirst();

        assertThat(supporterView.activitySharedWithMe()).isFalse();
        assertThat(supporterView.progressSharedWithMe()).isFalse();
    }

    @Test
    void acceptedRelationshipMissingGuardianConsentReportsNoAccessFromTheLearner() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(Year.now().getValue() - 10);
        LinkedLearnerRelationshipEntity relationship = acceptedRelationship(supporter, learner);
        LinkedLearnerGrantEntity activity = grant(
                relationship, learner.getId(), supporter.getId(), LinkedLearnerGrantScope.ACTIVITY);
        LinkedLearnerGrantEntity progress = grant(
                relationship, learner.getId(), supporter.getId(), LinkedLearnerGrantScope.PROGRESS);
        stubUser(supporter);
        stubUser(learner);
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                supporter.getId(), supporter.getId())).thenReturn(List.of(relationship));
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());
        when(grantRepository.findByRelationshipIdInAndScopeInAndRevokedAtIsNull(
                Set.of(relationship.getId()), List.of(
                        LinkedLearnerGrantScope.ACTIVITY, LinkedLearnerGrantScope.PROGRESS)))
                .thenReturn(List.of(activity, progress));

        LinkedLearnerResponse supporterView = service.list(supporter.getId()).getFirst();

        assertThat(supporterView.activitySharedWithMe()).isFalse();
        assertThat(supporterView.progressSharedWithMe()).isFalse();
    }

    /**
     * ⚠️ The consent gate is ASYMMETRIC and must stay so: it protects the LEARNER's data. A supporter
     * sharing their OWN activity with a learner who requires consent is not gated by it — exactly as
     * {@code requireGrant} applies the check only when {@code fromUserId} is the learner. Blanket-
     * applying it would wrongly hide the supporter's activity from the learner.
     */
    @Test
    void missingGuardianConsentDoesNotHideTheSupportersOwnSharedActivityFromTheLearner() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(Year.now().getValue() - 10);
        LinkedLearnerRelationshipEntity relationship = acceptedRelationship(supporter, learner);
        LinkedLearnerGrantEntity supporterActivity = grant(
                relationship, supporter.getId(), learner.getId(), LinkedLearnerGrantScope.ACTIVITY);
        stubUser(supporter);
        stubUser(learner);
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                learner.getId(), learner.getId())).thenReturn(List.of(relationship));
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());
        when(grantRepository.findByRelationshipIdInAndScopeInAndRevokedAtIsNull(
                Set.of(relationship.getId()), List.of(
                        LinkedLearnerGrantScope.ACTIVITY, LinkedLearnerGrantScope.PROGRESS)))
                .thenReturn(List.of(supporterActivity));

        LinkedLearnerResponse learnerView = service.list(learner.getId()).getFirst();

        assertThat(learnerView.activitySharedWithMe()).isTrue();
    }

    @Test
    void pendingRelationshipKeepsRowsVisibleByMeButReportsNoAccessWithMeForBothScopes() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(Year.now().getValue() - 10);
        LinkedLearnerRelationshipEntity relationship = acceptedRelationship(supporter, learner);
        relationship.setStatus(LinkedLearnerStatus.PENDING);
        LinkedLearnerGrantEntity activity = grant(
                relationship, learner.getId(), supporter.getId(), LinkedLearnerGrantScope.ACTIVITY);
        LinkedLearnerGrantEntity progress = grant(
                relationship, learner.getId(), supporter.getId(), LinkedLearnerGrantScope.PROGRESS);
        stubUser(supporter);
        stubUser(learner);
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                learner.getId(), learner.getId())).thenReturn(List.of(relationship));
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                supporter.getId(), supporter.getId())).thenReturn(List.of(relationship));
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());
        when(grantRepository.findByRelationshipIdInAndScopeInAndRevokedAtIsNull(
                Set.of(relationship.getId()), List.of(
                        LinkedLearnerGrantScope.ACTIVITY, LinkedLearnerGrantScope.PROGRESS)))
                .thenReturn(List.of(activity, progress));

        LinkedLearnerResponse learnerView = service.list(learner.getId()).getFirst();
        LinkedLearnerResponse supporterView = service.list(supporter.getId()).getFirst();

        assertThat(learnerView.activitySharedByMe()).isTrue();
        assertThat(learnerView.progressSharedByMe()).isTrue();
        assertThat(learnerView.activitySharedWithMe()).isFalse();
        assertThat(learnerView.progressSharedWithMe()).isFalse();
        assertThat(supporterView.activitySharedWithMe()).isFalse();
        assertThat(supporterView.progressSharedWithMe()).isFalse();
    }

    private LinkedLearnerGrantEntity grant(
            LinkedLearnerRelationshipEntity relationship,
            UUID fromUserId,
            UUID toUserId,
            LinkedLearnerGrantScope scope
    ) {
        LinkedLearnerGrantEntity grant = new LinkedLearnerGrantEntity();
        grant.setId(UUID.randomUUID());
        grant.setRelationshipId(relationship.getId());
        grant.setFromUserId(fromUserId);
        grant.setToUserId(toUserId);
        grant.setScope(scope);
        grant.setGrantedAt(OffsetDateTime.now());
        return grant;
    }

    /**
     * The birth-year decision now loads the learner through a PESSIMISTIC_WRITE read, so a test
     * that stubs only findById exercises none of it. Stub both, from one place, so a future test
     * cannot half-stub it and appear to pass.
     */
    private void stubUser(UserEntity user) {
        lenient().when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private void stubRelationshipUsers(
            LinkedLearnerRelationshipEntity relationship,
            UserEntity supporter,
            UserEntity learner
    ) {
        lenient().when(relationshipRepository.findById(relationship.getId()))
                .thenReturn(Optional.of(relationship));
        stubUser(supporter);
        stubUser(learner);
    }


    @Test
    void theInviteRateLimitIsCheckedBeforeAnythingIsWrittenOrSent() {
        // ⚠️ ORACLE GUARD, not just a limit test. The meter must sit at a point whose behaviour
        // cannot differ by whether the address has an account -- if a future change moves an
        // account lookup ahead of it, "which addresses get refused" becomes the same existence
        // oracle V122 closed. Pinning "nothing is written, nothing is sent" pins that ordering.
        UserEntity caller = user("caller@example.com");
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        doThrow(new AppException("TOO_MANY_INVITATIONS", "Too many.", HttpStatus.TOO_MANY_REQUESTS))
                .when(invitationRateLimitService).assertInviteAllowed(any(UUID.class), anyString());

        assertThatThrownBy(() -> service.invite(caller.getId(),
                new InviteLinkedLearnerRequest("target@example.com", LinkedLearnerSide.SUPPORTER, null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "TOO_MANY_INVITATIONS");

        verify(invitationRepository, never()).insertPendingIfAbsent(
                any(), any(), anyString(), anyString(), any(), any());
        verify(emailService, never()).sendEmail(any());
    }

    @Test
    void invitationListUsesABoundedOutgoingWindowAndKeepsIncomingLiveOnly() {
        UserEntity caller = user("caller@example.com");
        UserEntity inviter = user("inviter@example.com");
        LinkedLearnerInvitationEntity outgoingExpired = invitation(caller.getId(), "ignored@example.com");
        outgoingExpired.setExpiresAt(OffsetDateTime.now().minusDays(1));
        LinkedLearnerInvitationEntity incomingLive = invitation(inviter.getId(), caller.getEmail());
        incomingLive.setExpiresAt(OffsetDateTime.now().plusDays(4));
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        when(userRepository.findById(inviter.getId())).thenReturn(Optional.of(inviter));
        when(invitationRepository.findByInviterUserIdAndStatusAndExpiresAtAfter(
                eq(caller.getId()), eq(LinkedLearnerStatus.PENDING), any(OffsetDateTime.class)))
                .thenReturn(List.of(outgoingExpired));
        when(invitationRepository.findByInvitedEmailAndStatusAndExpiresAtAfter(
                eq(caller.getEmail()), eq(LinkedLearnerStatus.PENDING), any(OffsetDateTime.class)))
                .thenReturn(List.of(incomingLive));
        properties.getLinkedLearners().setInvitationTtlDays(11);

        OffsetDateTime before = OffsetDateTime.now();
        List<LinkedLearnerInvitationResponse> response = service.listInvitations(caller.getId());
        OffsetDateTime after = OffsetDateTime.now();

        assertThat(response).hasSize(2);
        LinkedLearnerInvitationResponse outgoing = response.getFirst();
        assertThat(outgoing.expired()).isTrue();
        assertThat(outgoing.expiresAt()).isEqualTo(outgoingExpired.getExpiresAt());
        assertThat(outgoing.inviterName()).isNull();
        LinkedLearnerInvitationResponse incoming = response.getLast();
        assertThat(incoming.expired()).isFalse();
        assertThat(incoming.inviterName()).isEqualTo(inviter.getDisplayName());

        ArgumentCaptor<OffsetDateTime> outgoingCutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(invitationRepository).findByInviterUserIdAndStatusAndExpiresAtAfter(
                eq(caller.getId()), eq(LinkedLearnerStatus.PENDING), outgoingCutoff.capture());
        assertThat(outgoingCutoff.getValue())
                .isBetween(before.minusDays(11), after.minusDays(11));

        ArgumentCaptor<OffsetDateTime> incomingCutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(invitationRepository).findByInvitedEmailAndStatusAndExpiresAtAfter(
                eq(caller.getEmail()), eq(LinkedLearnerStatus.PENDING), incomingCutoff.capture());
        assertThat(incomingCutoff.getValue()).isBetween(before, after);

        assertThat(Arrays.stream(LinkedLearnerInvitationResponse.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
                .containsExactly("id", "incoming", "inviterRole", "invitedEmail", "inviterName",
                        "createdAt", "expiresAt", "expired");
    }

    @Test
    void anExpiredInvitationCanStillBeRevokedByItsInviter() {
        UserEntity caller = user("caller@example.com");
        LinkedLearnerInvitationEntity expired = invitation(caller.getId(), "ignored@example.com");
        expired.setExpiresAt(OffsetDateTime.now().minusDays(1));
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        when(invitationRepository.findById(expired.getId())).thenReturn(Optional.of(expired));

        service.revokeInvitation(expired.getId(), caller.getId());

        verify(invitationRepository).markRevokedIfPending(eq(expired.getId()), any(OffsetDateTime.class));
    }

    @Test
    void anExpiredInvitationIsRefusedRatherThanAccepted() {
        // An invitation is a standing offer to whoever controls an ADDRESS. Without a bound, a
        // reassigned mailbox inherits the ability to accept a connection meant for someone else.
        UserEntity caller = user("invited@example.com");
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        LinkedLearnerInvitationEntity expired = invitation(UUID.randomUUID(), "invited@example.com");
        expired.setExpiresAt(OffsetDateTime.now().minusDays(1));
        when(invitationRepository.findById(expired.getId())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.acceptInvitation(
                expired.getId(), caller.getId(), new AcceptLinkedLearnerRequest(null, false)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "LINKED_LEARNER_INVITATION_EXPIRED");

        // ⚠️ No relationship, which is the point: expiry must cut the path to a cross-user read.
        verify(relationshipRepository, never()).insertPendingIfAbsent(
                any(), any(), any(), anyString(), any());
    }

    @Test
    void acceptanceClaimsTheInvitationBeforeCreatingTheRelationship() {
        // ⚠️ Ordering is the assertion. A revoke racing an accept would otherwise both read PENDING;
        // the accept would then build a relationship -- a live cross-user read -- behind an
        // invitation the other party had just revoked. Losing the CLAIM must abort before that.
        UserEntity caller = user("invited@example.com");
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        LinkedLearnerInvitationEntity live = invitation(UUID.randomUUID(), "invited@example.com");
        live.setExpiresAt(OffsetDateTime.now().plusDays(7));
        when(invitationRepository.findById(live.getId())).thenReturn(Optional.of(live));
        // 0 rows == somebody else moved it first.
        when(invitationRepository.markAcceptedIfPending(any(UUID.class), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.acceptInvitation(
                live.getId(), caller.getId(), new AcceptLinkedLearnerRequest(null, false)))
                .isInstanceOf(LinkedLearnerInvalidStateException.class);

        verify(relationshipRepository, never()).insertPendingIfAbsent(
                any(), any(), any(), anyString(), any());
    }

    @Test
    void reInvitingPassesAFreshExpiryAndNeverTouchesWhenTheAddressWasFirstInvited() {
        // insertPendingIfAbsent no-ops while a PENDING row exists, so a lapsed invitation would
        // otherwise block that address forever through the partial unique index.
        //
        // ⚠️ SCOPE OF THIS TEST, stated because an earlier version overstated it: the repository is
        // mocked, so the live-vs-expired discrimination — the `expires_at <= :now` guard — lives in
        // SQL and CANNOT be exercised here. That guard is covered by the migration probe against
        // real PostgreSQL, not by this test. What IS asserted here is the contract the service
        // controls: a fresh expiry is computed from the configured TTL, and createdAt is never
        // among the arguments, so re-arming cannot reset when the address was first invited.
        UserEntity caller = user("caller@example.com");
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        when(invitationRepository.findFirstByInviterUserIdAndInvitedEmailAndStatus(
                any(UUID.class), anyString(), eq(LinkedLearnerStatus.PENDING)))
                .thenReturn(Optional.of(invitation(caller.getId(), "target@example.com")));
        when(emailTemplateService.render(anyString(), anyMap())).thenReturn(
                new EmailTemplateService.RenderedEmailTemplate("Subject", "HTML", "Text"));
        properties.getLinkedLearners().setInvitationTtlDays(30);
        OffsetDateTime before = OffsetDateTime.now();

        service.invite(caller.getId(),
                new InviteLinkedLearnerRequest("target@example.com", LinkedLearnerSide.SUPPORTER, null));

        ArgumentCaptor<OffsetDateTime> expiresAt = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> now = ArgumentCaptor.forClass(OffsetDateTime.class);
        // ⚠️ EXACT role, not anyString(): the whole defect was re-arm reactivating the OLD
        // direction, so an assertion that tolerates any role cannot see the bug it is named for.
        verify(invitationRepository).reArmExpired(
                eq(caller.getId()), eq("target@example.com"), eq("SUPPORTER"),
                expiresAt.capture(), now.capture());

        // The new expiry is a full TTL ahead of the comparison instant — not a copy of it, and not
        // derived from the existing row, either of which would leave the invitation lapsed.
        assertThat(expiresAt.getValue()).isAfter(now.getValue().plusDays(29));
        assertThat(now.getValue()).isAfterOrEqualTo(before);

        // And the same fresh expiry is what a NEW invitation would be written with, so a re-armed
        // row and a first-time row get the same lifetime rather than two different rules.
        verify(invitationRepository).insertPendingIfAbsent(
                any(UUID.class), eq(caller.getId()), eq("target@example.com"), eq("SUPPORTER"),
                any(OffsetDateTime.class), eq(expiresAt.getValue()));
    }

    @Test
    void acceptingARelationshipRequiresAVerifiedEmail() {
        // ⚠️ v0.89.x wrote PENDING relationship rows by resolving an email to any ACTIVE account,
        // WITHOUT requiring that invitee to be verified. Those rows are live in production, and
        // accept() is the transition that turns one into a readable cross-user connection — so
        // gating only the new invitation endpoints would have left the old path wide open.
        UserEntity caller = user("legacy-invitee@example.com");
        doThrow(new AppException("EMAIL_NOT_VERIFIED", "Verify your email.", HttpStatus.FORBIDDEN))
                .when(authService).requireEmailVerified(caller.getId());

        assertThatThrownBy(() -> service.accept(
                UUID.randomUUID(), caller.getId(), new AcceptLinkedLearnerRequest(null, false)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_NOT_VERIFIED");

        verify(relationshipRepository, never()).findById(any(UUID.class));
        verify(relationshipRepository, never()).markAcceptedIfPending(any(UUID.class), any());
    }

    @Test
    void recordingGuardianConsentRequiresAVerifiedEmail() {
        // Consent is a persisted legal attestation and a precondition to ACCEPTED; an unproven
        // identity must not be able to write one on a minor's behalf.
        UserEntity caller = user("supporter@example.com");
        doThrow(new AppException("EMAIL_NOT_VERIFIED", "Verify your email.", HttpStatus.FORBIDDEN))
                .when(authService).requireEmailVerified(caller.getId());

        assertThatThrownBy(() -> service.recordGuardianConsent(UUID.randomUUID(), caller.getId()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_NOT_VERIFIED");

        verify(consentRepository, never()).save(any());
    }

    @Test
    void recordingABirthYearRequiresAVerifiedEmail() {
        UserEntity caller = user("learner@example.com");
        doThrow(new AppException("EMAIL_NOT_VERIFIED", "Verify your email.", HttpStatus.FORBIDDEN))
                .when(authService).requireEmailVerified(caller.getId());

        assertThatThrownBy(() -> service.recordBirthYear(UUID.randomUUID(), caller.getId(), 2010))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_NOT_VERIFIED");

        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void revokingStaysOpenToAnUnverifiedCaller() {
        // ⚠️ DELIBERATE ASYMMETRY. Revoke CUTS access; gating it would trap someone in a connection
        // they want out of, which harms the person the verified-email gate exists to protect.
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user("minor@example.com");
        learner.setBirthYear(2000);
        LinkedLearnerRelationshipEntity relationship =
                relationship(supporter, learner, LinkedLearnerSide.SUPPORTER);
        relationship.setStatus(LinkedLearnerStatus.ACCEPTED);
        stubRelationshipUsers(relationship, supporter, learner);

        service.revoke(relationship.getId(), learner.getId());

        assertThat(relationship.getStatus()).isEqualTo(LinkedLearnerStatus.REVOKED);
        verify(authService, never()).requireEmailVerified(learner.getId());
    }

    @Test
    void correctingABirthYearStaysOpenToAnUnverifiedCaller() {
        // ⚠️ SPLIT OUT because the combined test NEVER CALLED correctBirthYear — it was named for
        // two behaviours and exercised one, so the correction half would have passed even with a
        // verification gate bolted onto it. Gating correction would disable the v0.89.1 mechanism
        // that re-pauses links when a learner corrects downward into the consent range.
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user("minor@example.com");
        learner.setBirthYear(2000);
        LinkedLearnerRelationshipEntity relationship =
                relationship(supporter, learner, LinkedLearnerSide.SUPPORTER);
        relationship.setStatus(LinkedLearnerStatus.ACCEPTED);
        stubRelationshipUsers(relationship, supporter, learner);
        when(relationshipRepository.findByLearnerUserIdAndStatus(learner.getId(), LinkedLearnerStatus.ACCEPTED))
                .thenReturn(List.of(relationship));
        when(relationshipRepository.findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
                learner.getId(), learner.getId())).thenReturn(List.of(relationship));

        service.correctBirthYear(learner.getId(), Year.now().getValue() - 10);

        assertThat(learner.getBirthYear()).isEqualTo(Year.now().getValue() - 10);
        assertThat(relationship.getStatus()).isEqualTo(LinkedLearnerStatus.PENDING);
        verify(authService, never()).requireEmailVerified(learner.getId());
    }

    @Test
    void acceptingAnInvitationRequiresAVerifiedEmail() {
        // ⚠️ The hole email-keying itself created. An invited address may have no account yet — that
        // is the point — so without this gate anyone knowing the address could register it, skip the
        // inbox, accept, and become a SUPPORTER with a cross-user progress read.
        UserEntity caller = user("invited@example.com");
        doThrow(new AppException("EMAIL_NOT_VERIFIED", "Verify your email.", HttpStatus.FORBIDDEN))
                .when(authService).requireEmailVerified(caller.getId());

        // ⚠️ Assert the CODE, not the type. UserNotFoundException also extends AppException, so an
        // isInstanceOf(AppException.class) assertion passes even when the gate is removed entirely.
        assertThatThrownBy(() -> service.acceptInvitation(
                UUID.randomUUID(), caller.getId(), new AcceptLinkedLearnerRequest(null, false)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_NOT_VERIFIED");

        // ⚠️ Not even the user lookup runs: the gate short-circuits before anything else, which is
        // what makes it a gate rather than a check somewhere in the middle.
        verify(userRepository, never()).findById(any(UUID.class));
        verify(invitationRepository, never()).findById(any(UUID.class));
        verify(relationshipRepository, never()).insertPendingIfAbsent(any(), any(), any(), anyString(), any());
    }

    @Test
    void acceptingAnInvitationAddressedToSomeoneElseIsRefused() {
        UserEntity caller = user("attacker@example.com");
        LinkedLearnerInvitationEntity invitation = invitation(UUID.randomUUID(), "victim@example.com");
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.acceptInvitation(
                invitation.getId(), caller.getId(), new AcceptLinkedLearnerRequest(null, false)))
                .isInstanceOf(LinkedLearnerNotAllowedException.class);

        // ⚠️ Authorises on owning the ADDRESS, not on holding the id.
        verify(relationshipRepository, never()).insertPendingIfAbsent(any(), any(), any(), anyString(), any());
    }

    @Test
    void anAlreadyAcceptedInvitationCannotBeAcceptedAgain() {
        UserEntity caller = user("invited@example.com");
        LinkedLearnerInvitationEntity invitation = invitation(UUID.randomUUID(), "invited@example.com");
        invitation.setStatus(LinkedLearnerStatus.ACCEPTED);
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.acceptInvitation(
                invitation.getId(), caller.getId(), new AcceptLinkedLearnerRequest(null, false)))
                .isInstanceOf(LinkedLearnerInvalidStateException.class);
        verify(relationshipRepository, never()).insertPendingIfAbsent(any(), any(), any(), anyString(), any());
    }

    @Test
    void aLearnerInitiatedInviteCapturesTheLearnersOwnYearSoTheSupporterCanAccept() {
        // Without this the invitation is permanently un-acceptable: the supporter accepts, the
        // consent gate needs the LEARNER's year, only the learner may declare it, and before
        // acceptance there is no relationship id for the record-birth-year route to address.
        UserEntity caller = user("learner@example.com");
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        when(invitationRepository.findFirstByInviterUserIdAndInvitedEmailAndStatus(
                any(UUID.class), anyString(), eq(LinkedLearnerStatus.PENDING)))
                .thenReturn(Optional.of(invitation(caller.getId(), "supporter@example.com")));
        when(emailTemplateService.render(anyString(), anyMap())).thenReturn(
                new EmailTemplateService.RenderedEmailTemplate("Subject", "HTML", "Text"));

        service.invite(caller.getId(), new InviteLinkedLearnerRequest(
                "supporter@example.com", LinkedLearnerSide.LEARNER, 2000));

        assertThat(caller.getBirthYear()).isEqualTo(2000);
    }

    @Test
    void aLearnerInitiatedInviteWithoutAYearIsRefusedRatherThanCreatingADeadEnd() {
        UserEntity caller = user("learner@example.com");
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> service.invite(caller.getId(), new InviteLinkedLearnerRequest(
                "supporter@example.com", LinkedLearnerSide.LEARNER, null)))
                .isInstanceOf(LinkedLearnerBirthYearRequiredException.class);

        verify(invitationRepository, never()).insertPendingIfAbsent(any(), any(), anyString(), anyString(), any(), any());
    }

    private LinkedLearnerInvitationEntity invitation(UUID inviterUserId, String email) {
        LinkedLearnerInvitationEntity invitation = new LinkedLearnerInvitationEntity();
        invitation.setId(UUID.randomUUID());
        invitation.setInviterUserId(inviterUserId);
        invitation.setInvitedEmail(email);
        invitation.setInviterRole(LinkedLearnerSide.SUPPORTER);
        invitation.setStatus(LinkedLearnerStatus.PENDING);
        invitation.setCreatedAt(OffsetDateTime.now());
        return invitation;
    }

    private UserEntity user(String email) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setFirstName(email.substring(0, email.indexOf('@')));
        user.setDisplayName(user.getFirstName());
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private LinkedLearnerRelationshipEntity relationship(
            UserEntity supporter,
            UserEntity learner,
            LinkedLearnerSide initiatedBy
    ) {
        LinkedLearnerRelationshipEntity relationship = new LinkedLearnerRelationshipEntity();
        relationship.setId(UUID.randomUUID());
        relationship.setSupporterUserId(supporter.getId());
        relationship.setLearnerUserId(learner.getId());
        relationship.setStatus(LinkedLearnerStatus.PENDING);
        relationship.setInitiatedBy(initiatedBy);
        relationship.setCreatedAt(OffsetDateTime.now());
        relationshipsById.put(relationship.getId(), relationship);
        return relationship;
    }

    private LinkedLearnerRelationshipEntity acceptedRelationship(UserEntity supporter, UserEntity learner) {
        LinkedLearnerRelationshipEntity relationship = relationship(
                supporter, learner, LinkedLearnerSide.SUPPORTER);
        relationship.setStatus(LinkedLearnerStatus.ACCEPTED);
        relationship.setAcceptedAt(OffsetDateTime.now().minusDays(1));
        return relationship;
    }
}
