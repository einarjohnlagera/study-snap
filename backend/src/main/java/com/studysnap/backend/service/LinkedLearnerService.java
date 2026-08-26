package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.AcceptLinkedLearnerRequest;
import com.studysnap.backend.dto.InviteLinkedLearnerRequest;
import com.studysnap.backend.dto.LinkedLearnerBirthYearCorrectionPreviewResponse;
import com.studysnap.backend.dto.LinkedLearnerInvitationResponse;
import com.studysnap.backend.dto.LinkedLearnerResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.LinkedLearnerGuardianConsentEntity;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerSide;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.InvalidLinkedLearnerBirthYearException;
import com.studysnap.backend.exception.LinkedLearnerBirthYearCorrectionNotAllowedException;
import com.studysnap.backend.exception.LinkedLearnerBirthYearRequiredException;
import com.studysnap.backend.exception.LinkedLearnerInvalidStateException;
import com.studysnap.backend.exception.LinkedLearnerNotAllowedException;
import com.studysnap.backend.exception.LinkedLearnerNotFoundException;
import com.studysnap.backend.exception.LinkedLearnerSelfLinkException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.LinkedLearnerGuardianConsentRepository;
import com.studysnap.backend.entity.LinkedLearnerInvitationEntity;
import com.studysnap.backend.repository.LinkedLearnerInvitationRepository;
import com.studysnap.backend.repository.LinkedLearnerRelationshipRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LinkedLearnerService {
    public static final String GENERIC_INVITE_MESSAGE =
            "If this email belongs to an active NoteLib account, they will receive an invitation.";

    private static final List<LinkedLearnerStatus> LIVE_STATUSES =
            List.of(LinkedLearnerStatus.PENDING, LinkedLearnerStatus.ACCEPTED);
    private static final int MINIMUM_BIRTH_YEAR = 1900;
    private static final int MAXIMUM_PLAUSIBLE_BIRTH_YEAR = 9999;
    // PLACEHOLDER FOR COUNSEL: the attestation text and version are not a legal position.
    private static final String GUARDIAN_ATTESTATION_VERSION = "guardian-consent-placeholder-v1";
    private static final String INVITATION_TEMPLATE = "linked-learner-invitation";

    private final LinkedLearnerRelationshipRepository relationshipRepository;
    private final LinkedLearnerInvitationRepository invitationRepository;
    private final LinkedLearnerGuardianConsentRepository consentRepository;
    private final UserRepository userRepository;
    private final OnboardingGuardService onboardingGuardService;
    private final AuthService authService;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final StudySnapProperties properties;

    @Transactional
    public SimpleMessageResponse invite(UUID callerUserId, InviteLinkedLearnerRequest request) {
        // This endpoint emails a THIRD PARTY, so it must not be reachable from a throwaway
        // unverified account. QuizShareLinkService already requires this on its send paths; the
        // omission here made unsolicited mail-out cheap for an attacker.
        authService.requireEmailVerified(callerUserId);
        onboardingGuardService.assertProfileComplete(callerUserId);
        UserEntity caller = requireUser(callerUserId);
        String normalizedEmail = normalizeEmail(request.email());

        // ⚠️ Self-invite is refused BEFORE any account lookup, and deliberately so: the caller
        // already knows their own address exists, so this reveals nothing, while checking it after
        // a lookup would make the refusal itself depend on account state.
        if (normalizedEmail.equalsIgnoreCase(normalizeEmail(caller.getEmail()))) {
            throw new LinkedLearnerSelfLinkException();
        }

        // ⚠️ When the INVITER is the learner, their birth year must be captured now. The supporter
        // accepts later, and the consent gate requires the learner's own year — but only the learner
        // may declare it, and before acceptance there is no relationship id for the existing
        // record-birth-year route to address. Without this the invitation is permanently
        // un-acceptable: the supporter's accept throws forever with no recovery path.
        if (request.inviterRole() == LinkedLearnerSide.LEARNER && caller.getBirthYear() == null) {
            if (request.learnerBirthYear() == null) {
                throw new LinkedLearnerBirthYearRequiredException();
            }
            persistBirthYear(caller, request.learnerBirthYear());
        }

        // ⚠️ THE ROW IS WRITTEN WHETHER OR NOT THE ADDRESS HAS AN ACCOUNT. That is the whole point:
        // previously an unknown address wrote nothing while a real one wrote a PENDING relationship
        // visible in the inviter's own list, so "invite an address, read your list" was an
        // account-existence oracle. There is now no branch on existence to observe.
        invitationRepository.insertPendingIfAbsent(
                UUID.randomUUID(), callerUserId, normalizedEmail,
                request.inviterRole().name(), OffsetDateTime.now());

        LinkedLearnerInvitationEntity invitation = invitationRepository
                .findFirstByInviterUserIdAndInvitedEmailAndStatus(
                        callerUserId, normalizedEmail, LinkedLearnerStatus.PENDING)
                .orElseThrow(LinkedLearnerNotFoundException::new);

        // The email goes to the typed address either way. A recipient with no account follows the
        // link, signs up, and finds the invitation waiting — which is how a parent invites a child.
        sendInvitationEmail(caller, normalizedEmail, invitation);
        return genericInviteResponse();
    }

    /**
     * Pending invitations in both directions. Kept separate from {@link #list} because an invitation
     * is not a connection: merging them would invite a future reader to count invitations as
     * relationships, which is exactly what the open checkpoint must not have happen.
     */
    @Transactional(readOnly = true)
    public List<LinkedLearnerInvitationResponse> listInvitations(UUID callerUserId) {
        // Incoming invitations are matched on the caller's address, so an unverified account must
        // not be able to read invitations sent to an address it has merely claimed.
        authService.requireEmailVerified(callerUserId);
        onboardingGuardService.assertProfileComplete(callerUserId);
        UserEntity caller = requireUser(callerUserId);
        String callerEmail = normalizeEmail(caller.getEmail());

        List<LinkedLearnerInvitationResponse> outgoing = invitationRepository
                .findByInviterUserIdAndStatus(callerUserId, LinkedLearnerStatus.PENDING)
                .stream()
                .map(invitation -> new LinkedLearnerInvitationResponse(
                        invitation.getId(), false, invitation.getInviterRole(),
                        invitation.getInvitedEmail(),
                        // ⚠️ No name for an outgoing invitation. The inviter typed the address and
                        // must learn nothing further from it, or the list harvests names again.
                        null,
                        invitation.getCreatedAt()))
                .toList();

        List<LinkedLearnerInvitationResponse> incoming = invitationRepository
                .findByInvitedEmailAndStatus(callerEmail, LinkedLearnerStatus.PENDING)
                .stream()
                .filter(invitation -> !callerUserId.equals(invitation.getInviterUserId()))
                .map(invitation -> new LinkedLearnerInvitationResponse(
                        invitation.getId(), true, invitation.getInviterRole(),
                        invitation.getInvitedEmail(),
                        // The recipient DOES need to know who is asking in order to decide.
                        userRepository.findById(invitation.getInviterUserId())
                                .map(this::resolveDisplayName).orElse(null),
                        invitation.getCreatedAt()))
                .toList();

        return java.util.stream.Stream.concat(outgoing.stream(), incoming.stream()).toList();
    }

    @Transactional(readOnly = true)
    public List<LinkedLearnerResponse> list(UUID callerUserId) {
        onboardingGuardService.assertProfileComplete(callerUserId);
        return listRelationships(callerUserId);
    }

    @Transactional(readOnly = true)
    public LinkedLearnerBirthYearCorrectionPreviewResponse previewBirthYearCorrection(
            UUID callerUserId,
            int birthYear
    ) {
        onboardingGuardService.assertProfileComplete(callerUserId);
        validateCorrectionBirthYear(birthYear);
        UserEntity learner = requireUser(callerUserId);
        requireRecordedBirthYear(learner);
        return new LinkedLearnerBirthYearCorrectionPreviewResponse(
                relationshipsPausedByCorrection(learner, birthYear).size());
    }

    @Transactional
    public List<LinkedLearnerResponse> correctBirthYear(UUID callerUserId, int birthYear) {
        onboardingGuardService.assertProfileComplete(callerUserId);
        validateCorrectionBirthYear(birthYear);
        UserEntity learner = requireUser(callerUserId);
        requireRecordedBirthYear(learner);
        if (Integer.valueOf(birthYear).equals(learner.getBirthYear())) {
            return listRelationships(callerUserId);
        }

        List<LinkedLearnerRelationshipEntity> relationshipsToPause =
                relationshipsPausedByCorrection(learner, birthYear);
        OffsetDateTime now = OffsetDateTime.now();
        learner.setBirthYear(birthYear);
        learner.setBirthYearUpdatedAt(now);
        learner.setUpdatedAt(now);
        userRepository.save(learner);

        relationshipsToPause.forEach(relationship -> {
            relationship.setStatus(LinkedLearnerStatus.PENDING);
            relationship.setAcceptedAt(null);
        });
        relationshipRepository.saveAll(relationshipsToPause);
        return listRelationships(callerUserId);
    }

    /**
     * Accept an email-keyed invitation. This is the ONLY path that creates a relationship row, which
     * is what keeps {@code linked_learner_relationships} meaning what the open checkpoint counts —
     * an unresolved invitation is not a connection.
     *
     * <p>⚠️ The caller must own the invited ADDRESS, not merely hold the invitation id. The id is a
     * UUID and unguessable in practice, but authorising on possession of an identifier rather than
     * on identity is the shape that becomes a hole the moment an id leaks into a log or a referrer.
     *
     * <p>Relationship creation then delegates to {@link #accept}, so the guardian-consent gate,
     * the birth-year capture and the PENDING-until-consent rule are reused rather than reimplemented.
     */
    @Transactional
    public LinkedLearnerResponse acceptInvitation(
            UUID invitationId,
            UUID callerUserId,
            AcceptLinkedLearnerRequest request
    ) {
        // ⚠️ VERIFIED EMAIL IS THE WHOLE AUTHORIZATION HERE, and its absence was a hole created by
        // email-keying itself. Acceptance authorises on owning the invited ADDRESS — but an invited
        // address may have no account yet, which is the point. Without this gate anyone who knows
        // the address could register it, skip the inbox (signup returns a token and login never
        // checks verification), accept, and become a SUPPORTER with a cross-user progress read.
        // assertProfileComplete does NOT cover this: it passes for a brand-new account, because it
        // only fires when profileType is null AND onboarding is already complete.
        authService.requireEmailVerified(callerUserId);
        onboardingGuardService.assertProfileComplete(callerUserId);
        UserEntity caller = requireUser(callerUserId);
        LinkedLearnerInvitationEntity invitation = invitationRepository.findById(invitationId)
                .orElseThrow(LinkedLearnerNotFoundException::new);
        if (!normalizeEmail(caller.getEmail()).equalsIgnoreCase(invitation.getInvitedEmail())) {
            throw new LinkedLearnerNotAllowedException();
        }
        if (invitation.getStatus() != LinkedLearnerStatus.PENDING) {
            throw new LinkedLearnerInvalidStateException();
        }
        if (callerUserId.equals(invitation.getInviterUserId())) {
            throw new LinkedLearnerSelfLinkException();
        }

        UUID supporterUserId = invitation.getInviterRole() == LinkedLearnerSide.SUPPORTER
                ? invitation.getInviterUserId() : callerUserId;
        UUID learnerUserId = invitation.getInviterRole() == LinkedLearnerSide.LEARNER
                ? invitation.getInviterUserId() : callerUserId;

        OffsetDateTime now = OffsetDateTime.now();
        relationshipRepository.insertPendingIfAbsent(
                UUID.randomUUID(), supporterUserId, learnerUserId,
                invitation.getInviterRole().name(), now);
        LinkedLearnerRelationshipEntity relationship = relationshipRepository
                .findFirstBySupporterUserIdAndLearnerUserIdAndStatusIn(
                        supporterUserId, learnerUserId, LIVE_STATUSES)
                .orElseThrow(LinkedLearnerNotFoundException::new);

        invitation.setStatus(LinkedLearnerStatus.ACCEPTED);
        invitation.setAcceptedAt(now);
        invitationRepository.save(invitation);

        return accept(relationship.getId(), callerUserId, request);
    }

    /** Revoke a still-unaccepted invitation. Either the inviter or the invited address may do it. */
    @Transactional
    public SimpleMessageResponse revokeInvitation(UUID invitationId, UUID callerUserId) {
        authService.requireEmailVerified(callerUserId);
        onboardingGuardService.assertProfileComplete(callerUserId);
        UserEntity caller = requireUser(callerUserId);
        LinkedLearnerInvitationEntity invitation = invitationRepository.findById(invitationId)
                .orElseThrow(LinkedLearnerNotFoundException::new);
        boolean isInviter = callerUserId.equals(invitation.getInviterUserId());
        boolean isInvited = normalizeEmail(caller.getEmail()).equalsIgnoreCase(invitation.getInvitedEmail());
        if (!isInviter && !isInvited) {
            throw new LinkedLearnerNotAllowedException();
        }
        if (invitation.getStatus() == LinkedLearnerStatus.PENDING) {
            invitation.setStatus(LinkedLearnerStatus.REVOKED);
            invitation.setRevokedAt(OffsetDateTime.now());
            invitationRepository.save(invitation);
        }
        return genericInviteResponse();
    }

    @Transactional
    public LinkedLearnerResponse accept(
            UUID relationshipId,
            UUID callerUserId,
            AcceptLinkedLearnerRequest request
    ) {
        // ⚠️ VERIFIED EMAIL IS THE AUTHORIZATION. Invitations are keyed to an ADDRESS, so
        // control of that address is the only thing proving the caller is the invited party.
        // Signup issues a token without inbox access. Gate every path that GRANTS or WIDENS
        // access; revoke() and correctBirthYear() stay ungated because they CUT access and
        // gating them would disable the v0.89.1 safety mechanism.
        authService.requireEmailVerified(callerUserId);
        onboardingGuardService.assertProfileComplete(callerUserId);
        LinkedLearnerRelationshipEntity relationship = requireRelationship(relationshipId);
        requireInvitedParty(relationship, callerUserId);
        if (relationship.getStatus() != LinkedLearnerStatus.PENDING) {
            throw new LinkedLearnerInvalidStateException();
        }

        UserEntity learner = requireUser(relationship.getLearnerUserId());
        if (learner.getBirthYear() == null) {
            if (!callerUserId.equals(learner.getId()) || request.learnerBirthYear() == null) {
                throw new LinkedLearnerBirthYearRequiredException();
            }
            persistBirthYear(learner, request.learnerBirthYear());
        }

        boolean consentRequired = requiresGuardianConsent(learner.getBirthYear());
        boolean consentRecorded = consentRepository.findByRelationshipId(relationshipId).isPresent();
        if (consentRequired && !consentRecorded && request.guardianConsentAttested()
                && callerUserId.equals(relationship.getSupporterUserId())) {
            recordConsent(relationship, callerUserId);
            consentRecorded = true;
        }
        if (consentRequired && !consentRecorded) {
            return toResponse(relationship, callerUserId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        relationship.setStatus(LinkedLearnerStatus.ACCEPTED);
        relationship.setAcceptedAt(now);
        relationship.setRevokedAt(null);
        return toResponse(relationshipRepository.save(relationship), callerUserId);
    }

    @Transactional
    public LinkedLearnerResponse recordBirthYear(
            UUID relationshipId,
            UUID callerUserId,
            int birthYear
    ) {
        authService.requireEmailVerified(callerUserId);
        onboardingGuardService.assertProfileComplete(callerUserId);
        LinkedLearnerRelationshipEntity relationship = requireRelationship(relationshipId);
        requirePending(relationship);
        if (!callerUserId.equals(relationship.getLearnerUserId())) {
            throw new LinkedLearnerNotAllowedException();
        }
        UserEntity learner = requireUser(callerUserId);
        if (learner.getBirthYear() == null) {
            persistBirthYear(learner, birthYear);
        }
        return toResponse(relationship, callerUserId);
    }

    @Transactional
    public LinkedLearnerResponse recordGuardianConsent(UUID relationshipId, UUID callerUserId) {
        authService.requireEmailVerified(callerUserId);
        onboardingGuardService.assertProfileComplete(callerUserId);
        LinkedLearnerRelationshipEntity relationship = requireRelationship(relationshipId);
        requirePending(relationship);
        if (!callerUserId.equals(relationship.getSupporterUserId())) {
            throw new LinkedLearnerNotAllowedException();
        }
        UserEntity learner = requireUser(relationship.getLearnerUserId());
        if (learner.getBirthYear() == null) {
            throw new LinkedLearnerBirthYearRequiredException();
        }
        if (!requiresGuardianConsent(learner.getBirthYear())) {
            throw new LinkedLearnerNotAllowedException();
        }
        if (consentRepository.findByRelationshipId(relationshipId).isEmpty()) {
            recordConsent(relationship, callerUserId);
        }
        return toResponse(relationship, callerUserId);
    }

    @Transactional
    public LinkedLearnerResponse revoke(UUID relationshipId, UUID callerUserId) {
        onboardingGuardService.assertProfileComplete(callerUserId);
        LinkedLearnerRelationshipEntity relationship = requireRelationship(relationshipId);
        requireParty(relationship, callerUserId);
        if (relationship.getStatus() == LinkedLearnerStatus.REVOKED) {
            return toResponse(relationship, callerUserId);
        }
        relationship.setStatus(LinkedLearnerStatus.REVOKED);
        relationship.setRevokedAt(OffsetDateTime.now());
        return toResponse(relationshipRepository.save(relationship), callerUserId);
    }

    private void persistBirthYear(UserEntity learner, int birthYear) {
        int currentYear = Year.now().getValue();
        if (birthYear < MINIMUM_BIRTH_YEAR || birthYear > currentYear) {
            throw new InvalidLinkedLearnerBirthYearException();
        }
        learner.setBirthYear(birthYear);
        learner.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(learner);
    }

    private void validateCorrectionBirthYear(int birthYear) {
        if (birthYear < MINIMUM_BIRTH_YEAR || birthYear > MAXIMUM_PLAUSIBLE_BIRTH_YEAR) {
            throw new InvalidLinkedLearnerBirthYearException();
        }
    }

    private void requireRecordedBirthYear(UserEntity learner) {
        if (learner.getBirthYear() == null) {
            throw new LinkedLearnerBirthYearCorrectionNotAllowedException();
        }
    }

    private List<LinkedLearnerRelationshipEntity> relationshipsPausedByCorrection(
            UserEntity learner,
            int correctedBirthYear
    ) {
        Integer currentBirthYear = learner.getBirthYear();
        if (currentBirthYear == null
                || correctedBirthYear <= currentBirthYear
                || !requiresGuardianConsent(correctedBirthYear)) {
            return List.of();
        }
        return relationshipRepository
                .findByLearnerUserIdAndStatus(learner.getId(), LinkedLearnerStatus.ACCEPTED)
                .stream()
                .filter(relationship -> consentRepository.findByRelationshipId(relationship.getId()).isEmpty())
                .toList();
    }

    private List<LinkedLearnerResponse> listRelationships(UUID callerUserId) {
        return relationshipRepository
                .findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(callerUserId, callerUserId)
                .stream()
                .map(relationship -> toResponse(relationship, callerUserId))
                .toList();
    }

    private boolean requiresGuardianConsent(int birthYear) {
        int youngestPossibleAge = Year.now().getValue() - birthYear - 1;
        return youngestPossibleAge <= properties.getLinkedLearners().getGuardianConsentMaxAge();
    }

    private void recordConsent(LinkedLearnerRelationshipEntity relationship, UUID supporterUserId) {
        LinkedLearnerGuardianConsentEntity consent = new LinkedLearnerGuardianConsentEntity();
        consent.setId(UUID.randomUUID());
        consent.setRelationshipId(relationship.getId());
        consent.setLearnerUserId(relationship.getLearnerUserId());
        consent.setAttestedByUserId(supporterUserId);
        consent.setAttestedAt(OffsetDateTime.now());
        consent.setAttestationVersion(GUARDIAN_ATTESTATION_VERSION);
        consentRepository.save(consent);
    }

    private LinkedLearnerResponse toResponse(LinkedLearnerRelationshipEntity relationship, UUID callerUserId) {
        LinkedLearnerSide callerRole = callerUserId.equals(relationship.getSupporterUserId())
                ? LinkedLearnerSide.SUPPORTER : LinkedLearnerSide.LEARNER;
        requireParty(relationship, callerUserId);
        UUID counterpartyId = callerRole == LinkedLearnerSide.SUPPORTER
                ? relationship.getLearnerUserId() : relationship.getSupporterUserId();
        UserEntity counterparty = requireUser(counterpartyId);
        UserEntity learner = callerRole == LinkedLearnerSide.LEARNER ? requireUser(callerUserId) : counterparty;
        boolean consentRequired = learner.getBirthYear() != null
                && requiresGuardianConsent(learner.getBirthYear());
        boolean consentRecorded = consentRepository.findByRelationshipId(relationship.getId()).isPresent();
        boolean invitedParty = relationship.getInitiatedBy() != callerRole;

        return new LinkedLearnerResponse(
                relationship.getId(),
                callerRole,
                relationship.getInitiatedBy(),
                relationship.getStatus() == LinkedLearnerStatus.PENDING && invitedParty,
                // The name-harvesting oracle this once guarded against is closed at the source:
                // since v0.90.0 inviting an address creates NO relationship row, so a row existing
                // at all means the invited party accepted and both sides agreed to the link. Gating
                // on acceptedAt is now actively wrong -- a consent-pending link and a link re-paused
                // by correctBirthYear() both carry a null acceptedAt, which rendered a blank name
                // above the email on a real, mutually-agreed connection.
                resolveDisplayName(counterparty),
                counterparty.getEmail(),
                relationship.getStatus(),
                relationship.getCreatedAt(),
                relationship.getAcceptedAt(),
                relationship.getRevokedAt(),
                learner.getBirthYear() == null,
                consentRequired,
                consentRecorded
        );
    }

    private void sendInvitationEmail(
            UserEntity caller,
            String invitedEmail,
            LinkedLearnerInvitationEntity invitation
    ) {
        try {
            String baseUrl = properties.getBilling().getFrontendBaseUrl();
            String invitationUrl = baseUrl.replaceAll("/+$", "") + "/linked-learners";
            EmailTemplateService.RenderedEmailTemplate rendered = emailTemplateService.render(
                    INVITATION_TEMPLATE,
                    Map.of(
                            // ⚠️ Addressed generically. The recipient may have no account, and even
                            // when they do, personalising from their profile would make the EMAIL
                            // differ by account existence -- reopening, in the recipient's inbox,
                            // the same oracle the row-always-written change just closed.
                            "recipientName", "there",
                            "inviterName", sanitizeForOutboundEmail(resolveDisplayName(caller)),
                            "invitationUrl", invitationUrl
                    )
            );
            boolean sent = emailService.sendEmail(new EmailMessage(
                    invitedEmail, rendered.subject(), rendered.htmlBody(), rendered.textBody()));
            if (!sent) {
                log.warn("linked.learner.invitation.email not-sent invitationId={}", invitation.getId());
            }
        } catch (RuntimeException ex) {
            log.warn("linked.learner.invitation.email failed invitationId={} message={}",
                    invitation.getId(), ex.getMessage());
        }
    }

    private void requireInvitedParty(LinkedLearnerRelationshipEntity relationship, UUID callerUserId) {
        LinkedLearnerSide callerRole = callerUserId.equals(relationship.getSupporterUserId())
                ? LinkedLearnerSide.SUPPORTER
                : callerUserId.equals(relationship.getLearnerUserId()) ? LinkedLearnerSide.LEARNER : null;
        if (callerRole == null || callerRole == relationship.getInitiatedBy()) {
            throw new LinkedLearnerNotAllowedException();
        }
    }

    private void requireParty(LinkedLearnerRelationshipEntity relationship, UUID callerUserId) {
        if (!callerUserId.equals(relationship.getSupporterUserId())
                && !callerUserId.equals(relationship.getLearnerUserId())) {
            throw new LinkedLearnerNotAllowedException();
        }
    }

    private void requirePending(LinkedLearnerRelationshipEntity relationship) {
        if (relationship.getStatus() != LinkedLearnerStatus.PENDING) {
            throw new LinkedLearnerInvalidStateException();
        }
    }

    private LinkedLearnerRelationshipEntity requireRelationship(UUID relationshipId) {
        return relationshipRepository.findById(relationshipId)
                .orElseThrow(LinkedLearnerNotFoundException::new);
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    private SimpleMessageResponse genericInviteResponse() {
        return new SimpleMessageResponse(GENERIC_INVITE_MESSAGE);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The invitation is the ONLY email in the product that puts one user's self-chosen text into a
     * message addressed to a different user, and {@code EmailTemplateService.substitute} does not
     * escape -- {@code Matcher.quoteReplacement} protects regex replacement semantics, not HTML.
     * Without this, a display name (or an entirely unvalidated first/last name) could inject markup
     * into HTML delivered from NoteLib's own SPF/DKIM-signed domain, which is a phishing primitive.
     *
     * <p>Angle brackets are stripped rather than entity-escaped because one parameter map renders
     * BOTH the HTML and the plaintext body: escaping would leak {@code &amp;amp;} into the text
     * version. Stripping is safe for a name, which has no legitimate use for {@code <} or {@code >}.
     * Newlines and control characters go too, so the name cannot forge extra lines in the text body.
     *
     * <p>⚠️ Deliberately NOT applied inside {@code EmailTemplateService}: four existing templates
     * pass markup on purpose ({@code unsubscribeFooterHtml}, {@code imageNoticeHtml},
     * {@code studyPackList}, {@code weakConceptList}), so a blanket escape there would break them.
     * An escape-by-default convention for that service is the durable fix and is recorded as a
     * follow-up rather than attempted at signoff.
     */
    private String sanitizeForOutboundEmail(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[<>]", "")
                .replaceAll("\\p{Cntrl}", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String resolveDisplayName(UserEntity user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return fullName.isBlank() ? user.getEmail() : fullName;
    }

}
