package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.AcceptLinkedLearnerRequest;
import com.studysnap.backend.dto.InviteLinkedLearnerRequest;
import com.studysnap.backend.exception.LinkedLearnerInvalidStateException;
import com.studysnap.backend.exception.LinkedLearnerBirthYearRequiredException;
import org.springframework.http.HttpStatus;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.dto.LinkedLearnerResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.LinkedLearnerGuardianConsentEntity;
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
import com.studysnap.backend.entity.LinkedLearnerInvitationEntity;
import static org.mockito.ArgumentMatchers.eq;
import com.studysnap.backend.repository.LinkedLearnerInvitationRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkedLearnerServiceTest {
    private static final String SUPPORTER_EMAIL = "supporter@example.com";
    private static final String LEARNER_EMAIL = "learner@example.com";

    @Mock private LinkedLearnerRelationshipRepository relationshipRepository;
    @Mock private LinkedLearnerInvitationRepository invitationRepository;
    @Mock private LinkedLearnerGuardianConsentRepository consentRepository;
    @Mock private UserRepository userRepository;
    @Mock private OnboardingGuardService onboardingGuardService;
    @Mock private AuthService authService;
    @Mock private EmailService emailService;
    @Mock private EmailTemplateService emailTemplateService;

    private StudySnapProperties properties;
    private LinkedLearnerService service;

    @BeforeEach
    void setUp() {
        properties = new StudySnapProperties();
        service = new LinkedLearnerService(
                relationshipRepository,
                invitationRepository,
                consentRepository,
                userRepository,
                onboardingGuardService,
                authService,
                emailService,
                emailTemplateService,
                properties
        );
    }

    @Test
    void explicitAcceptanceByInvitedLearnerPersistsAcceptedStatus() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(2000);
        LinkedLearnerRelationshipEntity relationship = relationship(supporter, learner, LinkedLearnerSide.SUPPORTER);
        stubRelationshipUsers(relationship, supporter, learner);
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());
        when(relationshipRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LinkedLearnerResponse response = service.accept(
                relationship.getId(), learner.getId(), new AcceptLinkedLearnerRequest(null, false));

        assertThat(response.status()).isEqualTo(LinkedLearnerStatus.ACCEPTED);
        assertThat(relationship.getStatus()).isEqualTo(LinkedLearnerStatus.ACCEPTED);
        assertThat(relationship.getAcceptedAt()).isNotNull();
        verify(relationshipRepository).save(relationship);
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
                any(UUID.class), eq(caller.getId()), anyString(), anyString(), any(OffsetDateTime.class));
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
        verify(relationshipRepository, never()).save(any());
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
        verify(invitationRepository, never()).insertPendingIfAbsent(any(), any(), anyString(), anyString(), any());
    }

    @Test
    void eitherPartyCanRevokePendingAndRevocationIsIdempotent() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        LinkedLearnerRelationshipEntity relationship = relationship(supporter, learner, LinkedLearnerSide.SUPPORTER);
        stubRelationshipUsers(relationship, supporter, learner);
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());
        when(relationshipRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LinkedLearnerResponse revoked = service.revoke(relationship.getId(), learner.getId());
        LinkedLearnerResponse repeated = service.revoke(relationship.getId(), supporter.getId());

        assertThat(revoked.status()).isEqualTo(LinkedLearnerStatus.REVOKED);
        assertThat(repeated.status()).isEqualTo(LinkedLearnerStatus.REVOKED);
        verify(relationshipRepository).save(relationship);
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
        when(relationshipRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

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
        verify(relationshipRepository, never()).save(any());
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
        when(relationshipRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LinkedLearnerResponse response = service.accept(
                relationship.getId(), learner.getId(), new AcceptLinkedLearnerRequest(null, false));

        assertThat(response.status()).isEqualTo(LinkedLearnerStatus.ACCEPTED);
        assertThat(relationship.getStatus()).isEqualTo(LinkedLearnerStatus.ACCEPTED);
        verify(relationshipRepository).save(relationship);
    }

    @Test
    void changingConfiguredThresholdChangesConsentOutcome() {
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user(LEARNER_EMAIL);
        learner.setBirthYear(Year.now().getValue() - 20);
        LinkedLearnerRelationshipEntity relationship = relationship(supporter, learner, LinkedLearnerSide.SUPPORTER);
        stubRelationshipUsers(relationship, supporter, learner);
        when(consentRepository.findByRelationshipId(relationship.getId())).thenReturn(Optional.empty());
        when(relationshipRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
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
        verify(relationshipRepository).saveAll(List.of(relationship));
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
        verify(relationshipRepository, never()).saveAll(any());
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
        verify(relationshipRepository).saveAll(List.of());
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
        verify(relationshipRepository).saveAll(List.of());
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
        verify(relationshipRepository, never()).saveAll(any());
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
                new LinkedLearnerReadAuthorizationService(relationshipRepository);

        UUID authorizedLearnerId = readAuthorizationService.requireAcceptedLearnerId(
                supporter.getId(), relationship.getId());
        service.correctBirthYear(learner.getId(), Year.now().getValue() - 10);

        assertThat(authorizedLearnerId).isEqualTo(learner.getId());
        assertThatThrownBy(() -> readAuthorizationService.requireAcceptedLearnerId(
                supporter.getId(), relationship.getId()))
                .isInstanceOf(LinkedLearnerProgressNotFoundException.class);
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
                .when(relationshipRepository).saveAll(any());

        assertThatThrownBy(() -> service.correctBirthYear(
                learner.getId(), Year.now().getValue() - 10))
                .isInstanceOf(IllegalStateException.class);
        assertThat(LinkedLearnerService.class
                .getMethod("correctBirthYear", UUID.class, int.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void listResponseExposesNoLearnerActivityFields() {
        Set<String> componentNames = Arrays.stream(LinkedLearnerResponse.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .collect(Collectors.toSet());

        assertThat(componentNames).noneMatch(name ->
                name.contains("progress")
                        || name.contains("readiness")
                        || name.contains("score")
                        || name.contains("quiz")
                        || name.contains("note")
                        || name.contains("studypack")
                        || name.contains("concepthealth"));
    }

    private void stubRelationshipUsers(
            LinkedLearnerRelationshipEntity relationship,
            UserEntity supporter,
            UserEntity learner
    ) {
        when(relationshipRepository.findById(relationship.getId())).thenReturn(Optional.of(relationship));
        when(userRepository.findById(supporter.getId())).thenReturn(Optional.of(supporter));
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));
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
        verify(relationshipRepository, never()).save(any(LinkedLearnerRelationshipEntity.class));
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
    void revokingAndCorrectingABirthYearStayOpenToAnUnverifiedCaller() {
        // ⚠️ DELIBERATE ASYMMETRY, and the reason the gate is not applied uniformly: these two paths
        // CUT access. Blocking revoke would trap someone in a connection they want out of, and
        // blocking correctBirthYear would disable the v0.89.1 mechanism that re-pauses links when a
        // learner corrects downward into the consent range. Gating them would harm the person the
        // gate exists to protect.
        UserEntity supporter = user(SUPPORTER_EMAIL);
        UserEntity learner = user("minor@example.com");
        learner.setBirthYear(2000);
        LinkedLearnerRelationshipEntity relationship =
                relationship(supporter, learner, LinkedLearnerSide.SUPPORTER);
        relationship.setStatus(LinkedLearnerStatus.ACCEPTED);
        stubRelationshipUsers(relationship, supporter, learner);
        when(relationshipRepository.save(any(LinkedLearnerRelationshipEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.revoke(relationship.getId(), learner.getId());

        assertThat(relationship.getStatus()).isEqualTo(LinkedLearnerStatus.REVOKED);
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

        verify(invitationRepository, never()).insertPendingIfAbsent(any(), any(), anyString(), anyString(), any());
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
