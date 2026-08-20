package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.AcceptLinkedLearnerRequest;
import com.studysnap.backend.dto.InviteLinkedLearnerRequest;
import com.studysnap.backend.dto.LinkedLearnerBirthYearCorrectionPreviewResponse;
import com.studysnap.backend.dto.LinkedLearnerResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.LinkedLearnerGuardianConsentEntity;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerSide;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.exception.InvalidLinkedLearnerBirthYearException;
import com.studysnap.backend.exception.LinkedLearnerBirthYearCorrectionNotAllowedException;
import com.studysnap.backend.exception.LinkedLearnerBirthYearRequiredException;
import com.studysnap.backend.exception.LinkedLearnerInvalidStateException;
import com.studysnap.backend.exception.LinkedLearnerNotAllowedException;
import com.studysnap.backend.exception.LinkedLearnerNotFoundException;
import com.studysnap.backend.exception.LinkedLearnerSelfLinkException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.LinkedLearnerGuardianConsentRepository;
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
        UserEntity counterparty = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElse(null);
        if (counterparty == null) {
            return genericInviteResponse();
        }
        if (callerUserId.equals(counterparty.getId())) {
            throw new LinkedLearnerSelfLinkException();
        }

        UUID supporterUserId = request.inviterRole() == LinkedLearnerSide.SUPPORTER
                ? callerUserId : counterparty.getId();
        UUID learnerUserId = request.inviterRole() == LinkedLearnerSide.LEARNER
                ? callerUserId : counterparty.getId();
        relationshipRepository.insertPendingIfAbsent(
                UUID.randomUUID(), supporterUserId, learnerUserId,
                request.inviterRole().name(), OffsetDateTime.now());

        LinkedLearnerRelationshipEntity relationship = relationshipRepository
                .findFirstBySupporterUserIdAndLearnerUserIdAndStatusIn(
                        supporterUserId, learnerUserId, LIVE_STATUSES)
                .orElseThrow(LinkedLearnerNotFoundException::new);
        if (relationship.getStatus() == LinkedLearnerStatus.PENDING) {
            sendInvitationEmail(caller, counterparty, relationship);
        }
        return genericInviteResponse();
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

    @Transactional
    public LinkedLearnerResponse accept(
            UUID relationshipId,
            UUID callerUserId,
            AcceptLinkedLearnerRequest request
    ) {
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
                // Disclose the counterparty's NAME only once the link has actually been accepted.
                // Before that, an invite is an unverified assertion by the caller: echoing back the
                // resolved display name turned "invite an address, read your own list" into a
                // name-harvesting oracle over arbitrary emails, and a REVOKED row retained that name
                // permanently with no way for the victim to remove it. The email is still returned
                // because the caller supplied it themselves and learns nothing new from it.
                relationship.getAcceptedAt() != null ? resolveDisplayName(counterparty) : null,
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
            UserEntity counterparty,
            LinkedLearnerRelationshipEntity relationship
    ) {
        try {
            String baseUrl = properties.getBilling().getFrontendBaseUrl();
            String invitationUrl = baseUrl.replaceAll("/+$", "") + "/linked-learners";
            EmailTemplateService.RenderedEmailTemplate rendered = emailTemplateService.render(
                    INVITATION_TEMPLATE,
                    Map.of(
                            "recipientName", resolveFirstName(counterparty),
                            "inviterName", sanitizeForOutboundEmail(resolveDisplayName(caller)),
                            "invitationUrl", invitationUrl
                    )
            );
            boolean sent = emailService.sendEmail(new EmailMessage(
                    counterparty.getEmail(), rendered.subject(), rendered.htmlBody(), rendered.textBody()));
            if (!sent) {
                log.warn("linked.learner.invitation.email not-sent relationshipId={}", relationship.getId());
            }
        } catch (RuntimeException ex) {
            log.warn("linked.learner.invitation.email failed relationshipId={} message={}",
                    relationship.getId(), ex.getMessage());
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

    private String resolveFirstName(UserEntity user) {
        return user.getFirstName() == null || user.getFirstName().isBlank()
                ? "there" : user.getFirstName().trim();
    }
}
