package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.AcceptLinkedLearnerRequest;
import com.studysnap.backend.dto.InviteLinkedLearnerRequest;
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
    void unknownAndActiveEmailInvitesReturnIndistinguishableResponses() {
        UserEntity caller = user("caller@example.com");
        UserEntity counterparty = user("known@example.com");
        UserEntity inactive = user("inactive@example.com");
        inactive.setStatus(UserStatus.SUSPENDED);
        LinkedLearnerRelationshipEntity relationship = relationship(caller, counterparty, LinkedLearnerSide.SUPPORTER);
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("known@example.com")).thenReturn(Optional.of(counterparty));
        when(userRepository.findByEmailIgnoreCase("inactive@example.com")).thenReturn(Optional.of(inactive));
        when(relationshipRepository.findFirstBySupporterUserIdAndLearnerUserIdAndStatusIn(
                caller.getId(), counterparty.getId(), List.of(LinkedLearnerStatus.PENDING, LinkedLearnerStatus.ACCEPTED)))
                .thenReturn(Optional.of(relationship));
        when(emailTemplateService.render(anyString(), any())).thenReturn(
                new EmailTemplateService.RenderedEmailTemplate("Subject", "HTML", "Text"));

        SimpleMessageResponse unknown = service.invite(
                caller.getId(), new InviteLinkedLearnerRequest("unknown@example.com", LinkedLearnerSide.SUPPORTER));
        SimpleMessageResponse known = service.invite(
                caller.getId(), new InviteLinkedLearnerRequest("known@example.com", LinkedLearnerSide.SUPPORTER));
        SimpleMessageResponse inactiveResponse = service.invite(
                caller.getId(), new InviteLinkedLearnerRequest("inactive@example.com", LinkedLearnerSide.SUPPORTER));

        assertThat(known).isEqualTo(unknown);
        assertThat(inactiveResponse).isEqualTo(unknown);
        // ⚠️ The three assertions above compare references to one shared constant, so they cannot
        // fail while that constant exists — they say nothing about the row write in the same method.
        // The observable difference an attacker actually uses is STATE, so assert that directly:
        // a real account gets a row, an unknown or inactive address gets none.
        verify(relationshipRepository, times(1)).insertPendingIfAbsent(
                any(UUID.class), any(UUID.class), any(UUID.class), anyString(), any(OffsetDateTime.class));
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
        when(userRepository.findByEmailIgnoreCase("known@example.com")).thenReturn(Optional.of(counterparty));
        when(relationshipRepository.findFirstBySupporterUserIdAndLearnerUserIdAndStatusIn(
                any(UUID.class), any(UUID.class), anyList())).thenReturn(Optional.of(pending));
        when(emailTemplateService.render(anyString(), anyMap())).thenReturn(
                new EmailTemplateService.RenderedEmailTemplate("Subject", "HTML", "Text"));

        service.invite(caller.getId(),
                new InviteLinkedLearnerRequest("known@example.com", LinkedLearnerSide.SUPPORTER));

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
                callerUserId, new InviteLinkedLearnerRequest("someone@example.com", LinkedLearnerSide.SUPPORTER)))
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
    void selfLinkingIsRefused() {
        UserEntity caller = user("same@example.com");
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        when(userRepository.findByEmailIgnoreCase("same@example.com")).thenReturn(Optional.of(caller));
        InviteLinkedLearnerRequest request = new InviteLinkedLearnerRequest(
                "same@example.com", LinkedLearnerSide.SUPPORTER);

        assertThatThrownBy(() -> service.invite(caller.getId(), request))
                .isInstanceOf(LinkedLearnerSelfLinkException.class);
        verify(relationshipRepository, never()).insertPendingIfAbsent(any(), any(), any(), anyString(), any());
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
