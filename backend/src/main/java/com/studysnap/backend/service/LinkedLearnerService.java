package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.AcceptLinkedLearnerRequest;
import com.studysnap.backend.dto.InviteLinkedLearnerRequest;
import com.studysnap.backend.dto.LinkedLearnerBirthYearCorrectionPreviewResponse;
import com.studysnap.backend.dto.LinkedLearnerInvitationResponse;
import com.studysnap.backend.dto.LinkedLearnerResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.LinkedLearnerGuardianConsentEntity;
import com.studysnap.backend.entity.LinkedLearnerGrantEntity;
import com.studysnap.backend.entity.LinkedLearnerGrantScope;
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
import com.studysnap.backend.exception.LinkedLearnerOnboardingRequiredException;
import com.studysnap.backend.exception.LinkedLearnerInvitationExpiredException;
import com.studysnap.backend.exception.LinkedLearnerSelfLinkException;
import com.studysnap.backend.security.InvitationRateLimitService;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.LinkedLearnerGuardianConsentRepository;
import com.studysnap.backend.repository.LinkedLearnerGrantRepository;
import com.studysnap.backend.entity.LinkedLearnerInvitationEntity;
import com.studysnap.backend.repository.LinkedLearnerInvitationRepository;
import com.studysnap.backend.repository.LinkedLearnerProvisionalBirthYearRepository;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LinkedLearnerService {
    public static final String GENERIC_INVITE_MESSAGE =
            // ⚠️ CONSTANT, and identical for every address — that is what keeps it from being an
            // account-existence oracle. It must also not claim an account is required: since V122
            // an invitation waits against the ADDRESS, which is the point of the feature.
            "Invitation sent. If they do not have a NoteLib account yet, it will be waiting when they sign up.";

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
    private final LinkedLearnerGrantRepository grantRepository;
    private final LinkedLearnerProvisionalBirthYearRepository provisionalBirthYearRepository;
    private final UserRepository userRepository;
    private final OnboardingGuardService onboardingGuardService;
    private final AuthService authService;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final StudySnapProperties properties;
    private final GuardianConsentPolicy guardianConsentPolicy;
    private final InvitationRateLimitService invitationRateLimitService;

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
        if (request.inviterRole() == LinkedLearnerSide.LEARNER) {
            // ⚠️ THE NULL CHECK MUST HAPPEN INSIDE THE LOCK. Reading caller.getBirthYear() from the
            // entity loaded above and then locking would let a concurrent accept/record commit a
            // MINOR year in between, which this write would overwrite with the adult year the
            // inviter typed — permanently, since the value is account-global and write-once, and
            // invite has no pass that re-evaluates existing links.
            if (lockAndReadBirthYear(callerUserId) == null) {
                if (request.learnerBirthYear() == null) {
                    throw new LinkedLearnerBirthYearRequiredException();
                }
                persistBirthYear(callerUserId, request.learnerBirthYear());
            }
        }

        // ⚠️ Metered HERE: after the verified-email gate, before anything is written or sent, and
        // keyed only on caller + address. It must never depend on whether the address has an
        // account — branching on that is precisely the oracle V122 closed.
        invitationRateLimitService.assertInviteAllowed(callerUserId, normalizedEmail);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusDays(properties.getLinkedLearners().getInvitationTtlDays());

        // ⚠️ THE ROW IS WRITTEN WHETHER OR NOT THE ADDRESS HAS AN ACCOUNT. That is the whole point:
        // previously an unknown address wrote nothing while a real one wrote a PENDING relationship
        // visible in the inviter's own list, so "invite an address, read your list" was an
        // account-existence oracle. There is now no branch on existence to observe.
        invitationRepository.insertPendingIfAbsent(
                UUID.randomUUID(), callerUserId, normalizedEmail,
                request.inviterRole().name(), now, expiresAt);

        // The insert no-ops when a live row already exists. If that row has LAPSED it would
        // otherwise block this address forever through the partial unique index, so re-arm it —
        // extending expiry only, never createdAt, which records the first invitation.
        invitationRepository.reArmExpired(
                callerUserId, normalizedEmail, request.inviterRole().name(), expiresAt, now);

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
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime outgoingVisibilityCutoff = now.minusDays(
                properties.getLinkedLearners().getInvitationTtlDays());

        List<LinkedLearnerInvitationResponse> outgoing = invitationRepository
                .findByInviterUserIdAndStatusAndExpiresAtAfter(
                        callerUserId, LinkedLearnerStatus.PENDING, outgoingVisibilityCutoff)
                .stream()
                .map(invitation -> new LinkedLearnerInvitationResponse(
                        invitation.getId(), false, invitation.getInviterRole(),
                        invitation.getInvitedEmail(),
                        // ⚠️ No name for an outgoing invitation. The inviter typed the address and
                        // must learn nothing further from it, or the list harvests names again.
                        null,
                        invitation.getCreatedAt(),
                        invitation.getExpiresAt(),
                        !invitation.getExpiresAt().isAfter(now)))
                .toList();

        List<LinkedLearnerInvitationResponse> incoming = invitationRepository
                .findByInvitedEmailAndStatusAndExpiresAtAfter(
                        callerEmail, LinkedLearnerStatus.PENDING, now)
                .stream()
                .filter(invitation -> !callerUserId.equals(invitation.getInviterUserId()))
                .map(invitation -> new LinkedLearnerInvitationResponse(
                        invitation.getId(), true, invitation.getInviterRole(),
                        invitation.getInvitedEmail(),
                        // The recipient DOES need to know who is asking in order to decide.
                        userRepository.findById(invitation.getInviterUserId())
                                .map(this::resolveDisplayName).orElse(null),
                        invitation.getCreatedAt(),
                        invitation.getExpiresAt(),
                        false))
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
        // ⚠️ Deliberately UNLOCKED: this is an advisory preview on a read-only path, and taking a
        // write lock here would block acceptance behind someone merely looking at the warning text.
        // A preview that is momentarily stale is acceptable; correctBirthYear re-reads under the
        // lock before acting on anything.
        Integer currentBirthYear = userRepository.findBirthYearById(callerUserId).orElse(null);
        requireRecordedBirthYear(currentBirthYear);
        return new LinkedLearnerBirthYearCorrectionPreviewResponse(
                relationshipsPausedByCorrection(callerUserId, currentBirthYear, birthYear).size());
    }

    @Transactional
    public List<LinkedLearnerResponse> correctBirthYear(UUID callerUserId, int birthYear) {
        onboardingGuardService.assertProfileComplete(callerUserId);
        validateCorrectionBirthYear(birthYear);
        // ⚠️ Same lock as accept(), taken FIRST. Holding it to commit is what guarantees an
        // acceptance that committed just before us is VISIBLE below and therefore paused, and that
        // one arriving after us reads the corrected year instead of the value we replaced.
        Integer currentBirthYear = lockAndReadBirthYear(callerUserId);
        requireRecordedBirthYear(currentBirthYear);
        if (Integer.valueOf(birthYear).equals(currentBirthYear)) {
            return listRelationships(callerUserId);
        }

        List<LinkedLearnerRelationshipEntity> relationshipsToPause =
                relationshipsPausedByCorrection(callerUserId, currentBirthYear, birthYear);
        OffsetDateTime now = OffsetDateTime.now();
        userRepository.writeBirthYear(callerUserId, birthYear, now);

        // ⚠️ Conditional per row rather than saveAll: a revoke committing between the select above
        // and this write would otherwise be overwritten back to PENDING, resurrecting a connection
        // the learner had just ended.
        relationshipsToPause.forEach(
                relationship -> relationshipRepository.pauseAcceptedForConsent(relationship.getId()));
        return listRelationships(callerUserId);
    }

    /**
     * Accept an email-keyed invitation and create its relationship row. Shareable-link redemption
     * deliberately reuses the same pending-relationship helper below; an unresolved email invitation
     * still creates no relationship row and remains outside the checkpoint denominator.
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
        // ⚠️ ONBOARDING IS REQUIRED HERE TOO, as of v0.98.0. This path forms the IDENTICAL relationship
        // as an invitation-link redemption — same guardian-consent handling, same cross-user capacity —
        // and until now answered to a lower bar, which the comment above already half-records:
        // assertProfileComplete "passes for a brand-new account".
        //
        // ⚠️ IT REJECTS TWO LIVE COHORTS, KNOWN IN ADVANCE. The frontend gates on needsOnboarding(),
        // which is NOT this predicate: a failed completeOnboarding POST (whose marker never retries) and
        // the copy-on-signup cohort (whose dashboard prompt is dismissible) both read as onboarded
        // client-side while this column stays null. Both have VERIFIED emails, so requireEmailVerified
        // above does not already stop them — checked, not assumed. The connections page routes a
        // COMPLETE_ONBOARDING remedy to /onboarding so this is a waypoint, not a wall.
        //
        // ⚠️ SCOPED TO THIS ACTING ENDPOINT ONLY. listInvitations and list are deliberately NOT
        // tightened: making an invitation invisible to its recipient converts an error into an absence,
        // and an absence gives them nothing to act on. v0.90.0 made invitations visible on purpose.
        //
        // ⚠️ BEFORE the invitation lookup below, so a rejection discloses only the caller's own account
        // state and never whether an invitation exists.
        if (caller.getOnboardingCompletedAt() == null) {
            throw new LinkedLearnerOnboardingRequiredException();
        }
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

        OffsetDateTime now = OffsetDateTime.now();
        if (!invitation.getExpiresAt().isAfter(now)) {
            throw new LinkedLearnerInvitationExpiredException();
        }

        // Read what the relationship needs BEFORE the update. The conditional update is a native
        // query, so the managed entity keeps its load-time status afterwards; taking these now
        // means nothing downstream depends on a stale row, rather than depending on it harmlessly.
        LinkedLearnerSide inviterRole = invitation.getInviterRole();
        UUID inviterUserId = invitation.getInviterUserId();

        // ⚠️ CLAIM THE INVITATION FIRST, and only build the relationship if this call won. Without
        // it, an accept racing a revoke could create a relationship — a live cross-user read —
        // behind an invitation the other party had just revoked.
        //
        // ⚠️ Note the ACTUAL mechanism, because it is not what a bare "0 rows means someone else
        // won" reading suggests: this runs inside a transaction, so a concurrent revoke BLOCKS on
        // the row lock this update takes rather than observing PENDING and racing it. The
        // zero-rows branch is the guard for the already-terminal case (an invitation accepted or
        // revoked in an earlier, committed transaction). Both are handled; they are different paths.
        if (invitationRepository.markAcceptedIfPending(invitation.getId(), now, now) == 0) {
            throw new LinkedLearnerInvalidStateException();
        }

        PendingRelationshipCreation creation = createPendingRelationship(
                inviterUserId, callerUserId, inviterRole, now);
        LinkedLearnerRelationshipEntity relationship = creation.relationship();

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
        // Conditional, and idempotent by design: 0 rows means it was already accepted or revoked,
        // which is not an error for a revoke. The generic response keeps those indistinguishable.
        invitationRepository.markRevokedIfPending(invitation.getId(), OffsetDateTime.now());
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

        // ⚠️ LOCK THE LEARNER BEFORE READING THE BIRTH YEAR. The consent decision is made from
        // this value, and correctBirthYear can move it into the minor range concurrently. Reading
        // it unlocked let acceptance decide "no consent needed" on an adult year, a correction
        // commit a minor year, and acceptance then finish ACCEPTED — a minor with a live supporter
        // connection and no consent record. Locking serializes the two in BOTH orders: if we win,
        // correctBirthYear's own lock makes it observe this acceptance and pause it; if it wins,
        // this read returns the corrected year and consent is required below.
        UUID learnerUserId = relationship.getLearnerUserId();
        Integer birthYear = resolveEffectiveBirthYearForDecision(relationship);
        if (birthYear == null) {
            if (!callerUserId.equals(learnerUserId) || request.learnerBirthYear() == null) {
                throw new LinkedLearnerBirthYearRequiredException();
            }
            birthYear = request.learnerBirthYear();
            persistBirthYear(learnerUserId, birthYear);
        }

        boolean consentRequired = guardianConsentPolicy.requiresGuardianConsent(birthYear);
        boolean consentRecorded = consentRepository.findByRelationshipId(relationshipId).isPresent();
        if (consentRequired && !consentRecorded && request.guardianConsentAttested()
                && callerUserId.equals(relationship.getSupporterUserId())) {
            recordConsent(relationship, callerUserId);
            consentRecorded = true;
        }
        if (consentRequired && !consentRecorded) {
            return toResponse(relationship, callerUserId);
        }

        // ⚠️ Conditional, so a revoke that committed while we were deciding is not overwritten.
        // Zero rows means the relationship is no longer PENDING — revoked by the other party, or
        // accepted elsewhere — and we must NOT report success from the entity we loaded.
        OffsetDateTime now = OffsetDateTime.now();
        if (relationshipRepository.markAcceptedIfPending(relationshipId, now) == 0) {
            throw new LinkedLearnerInvalidStateException();
        }
        // Promotion belongs to the successful transition, never the consent-pending early return.
        // The native update is write-once and relationship/status scoped; cleanup is idempotent.
        provisionalBirthYearRepository.promoteIfAccountBirthYearMissing(
                relationshipId, learnerUserId, now);
        provisionalBirthYearRepository.deleteForRelationship(relationshipId);
        // ⚠️ AND every OTHER declaration this learner holds, now that the account-global year exists.
        // A learner can hold more than one — two link redemptions before either creator confirms —
        // and deleting only this relationship's row leaves a declared value retained after the
        // account column is written, which v0.89.1 forbids. The statement is guarded on
        // users.birth_year being present, so it cannot run before promotion has succeeded.
        // ⚠️ ORDER MATTERS: this runs AFTER promotion, never before. The sibling row is inert by then
        // because findEffectiveBirthYear coalesces the account column first, so this is a retention
        // fix rather than a behaviour change.
        provisionalBirthYearRepository.deleteAllForLearnerOncePromoted(learnerUserId);
        // The conditional update cleared the persistence context, so re-read rather than trust the
        // detached copy — status is exactly the field that changed.
        return toResponse(requireRelationship(relationshipId), callerUserId);
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
        // Every writer of users.birth_year takes the same lock; skipping one would reopen the
        // correction race through that path instead of closing it.
        if (lockAndReadBirthYear(callerUserId) == null) {
            persistBirthYear(callerUserId, birthYear);
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
        Integer birthYear = resolveEffectiveBirthYearForDecision(relationship);
        if (birthYear == null) {
            throw new LinkedLearnerBirthYearRequiredException();
        }
        if (!guardianConsentPolicy.requiresGuardianConsent(birthYear)) {
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
        if (relationship.getStatus() == LinkedLearnerStatus.REVOKED
                || relationship.getStatus() == LinkedLearnerStatus.EXPIRED) {
            // Both terminal states are idempotent. In particular, an expiry is not rewritten as a
            // deliberate revocation and keeps its honest status for both parties.
            // ⚠️ Still cut grants here, and the reason is not defensiveness: revoke did NOT cut them
            // before v0.97.0, so a relationship revoked earlier keeps live rows and reports
            // *SharedByMe: true forever, since this path never reaches the transition below. The
            // statement is idempotent, so this heals those rows without touching anything else.
            grantRepository.revokeAllLiveForRelationship(relationshipId, OffsetDateTime.now());
            return toResponse(relationship, callerUserId);
        }
        // Take the learner lock EXPLICITLY, for ordering rather than for the value. Revocation is
        // now part of the provisional-year lifecycle, and acceptance holds this same lock while it
        // promotes then deletes; without it a revoke could delete the provisional row between
        // acceptance's read and its promotion, leaving an ACCEPTED relationship whose learner has no
        // account-global year — the state requireGrant denies with no remediation path.
        lockAndReadBirthYear(relationship.getLearnerUserId());
        // ⚠️ Conditional on BOTH live statuses, so revocation still wins when an acceptance
        // committed first. Zero rows means a terminal transition won, which is not an error — report
        // the persisted state rather than the stale one, and do not delete its provisional row.
        if (relationshipRepository.markRevokedIfLive(relationshipId, OffsetDateTime.now()) == 1) {
            // ⚠️ TERMINAL, so every live grant on this relationship ends — both directions, every
            // scope, one rule shared with expiry. Guarded by the SAME zero-row check as the
            // provisional delete: if an acceptance won the race, this call must not cut the grants
            // of a relationship that is now ACCEPTED.
            // ⚠️ NEVER do this on the ACCEPTED -> PENDING consent pause. v0.93.0 made the row
            // survive that pause by design, so the learner's own toggle keeps reading ON and
            // sharing resumes on re-acceptance.
            grantRepository.revokeAllLiveForRelationship(relationshipId, OffsetDateTime.now());
            provisionalBirthYearRepository.deleteForRelationship(relationshipId);
        }
        return toResponse(requireRelationship(relationshipId), callerUserId);
    }

    /**
     * Shared relationship-creation path for email invitations and single-use invitation links.
     * For a link redemption the REDEEMER is the initiator, so the creator is the existing accept
     * machinery's invited party and must confirm before this PENDING row can become ACCEPTED.
     */
    PendingRelationshipCreation createPendingRelationship(
            UUID initiatorUserId,
            UUID counterpartyUserId,
            LinkedLearnerSide initiatorRole,
            OffsetDateTime createdAt
    ) {
        UUID supporterUserId = initiatorRole == LinkedLearnerSide.SUPPORTER
                ? initiatorUserId : counterpartyUserId;
        UUID learnerUserId = initiatorRole == LinkedLearnerSide.LEARNER
                ? initiatorUserId : counterpartyUserId;
        OffsetDateTime expiresAt = createdAt.plusDays(
                properties.getLinkedLearners().getRequestTtlDays());
        int inserted = relationshipRepository.insertPendingIfAbsent(
                UUID.randomUUID(), supporterUserId, learnerUserId, initiatorRole.name(), createdAt, expiresAt);
        LinkedLearnerRelationshipEntity relationship = relationshipRepository
                .findFirstBySupporterUserIdAndLearnerUserIdAndStatusIn(
                        supporterUserId, learnerUserId, LIVE_STATUSES)
                .orElseThrow(LinkedLearnerNotFoundException::new);
        return new PendingRelationshipCreation(relationship, inserted == 1);
    }

    /**
     * Creator-side capture only: a learner creating a link deliberately acts on their own account.
     * Link redemption must use the provisional methods below and must never reuse this writer.
     */
    void captureLearnerBirthYearIfMissing(UUID learnerUserId, Integer suppliedBirthYear) {
        if (lockAndReadBirthYear(learnerUserId) != null) {
            return;
        }
        if (suppliedBirthYear == null) {
            throw new LinkedLearnerBirthYearRequiredException();
        }
        persistBirthYear(learnerUserId, suppliedBirthYear);
    }

    /**
     * Validate before the link is claimed, then lock the learner row for the rest of redemption.
     * A null return means the account-global year already exists and no provisional row is needed.
     */
    Integer prepareProvisionalBirthYearForLinkRedemption(
            UUID learnerUserId,
            Integer suppliedBirthYear
    ) {
        if (suppliedBirthYear != null) {
            validateBirthYear(suppliedBirthYear);
        }
        if (lockAndReadBirthYear(learnerUserId) != null) {
            return null;
        }
        if (suppliedBirthYear == null) {
            throw new LinkedLearnerBirthYearRequiredException();
        }
        return suppliedBirthYear;
    }

    void storeProvisionalBirthYear(
            UUID relationshipId,
            UUID learnerUserId,
            int birthYear,
            OffsetDateTime declaredAt
    ) {
        if (provisionalBirthYearRepository.insertIfAccountBirthYearMissing(
                relationshipId, learnerUserId, birthYear, declaredAt) != 1) {
            throw new LinkedLearnerInvalidStateException();
        }
    }

    record PendingRelationshipCreation(
            LinkedLearnerRelationshipEntity relationship,
            boolean inserted
    ) {
    }

    /**
     * The single way to load a learner whose birth year a consent decision depends on. Only ONE row
     * is ever locked, and it is always the learner's, so no lock cycle exists and no deadlock is
     * possible between these paths.
     */
    /**
     * Take the learner's row lock and return the birth year THE ROW ACTUALLY HOLDS.
     *
     * <p>⚠️ The two steps are separate on purpose, and collapsing them back into one entity read
     * reintroduces the defect. {@code findByIdForUpdate} acquires the lock correctly, but if the
     * user is already managed — and it always is, because the verified-email check and the
     * onboarding guard both load it first — Hibernate hands back the cached instance and throws
     * away the state it just read. The consent decision would then be made from the PRE-LOCK value:
     * acceptance reads an adult year, a correction into the minor range commits, and acceptance
     * still finishes ACCEPTED. The scalar read cannot come from the identity map, so it is the
     * value the lock actually protects.
     */
    private Integer lockAndReadBirthYear(UUID learnerUserId) {
        userRepository.findByIdForUpdate(learnerUserId).orElseThrow(UserNotFoundException::new);
        return userRepository.findBirthYearById(learnerUserId).orElse(null);
    }

    /**
     * The single path for relationship-scoped consent DECISIONS. The learner lock and its separate
     * scalar read MUST remain before the effective lookup; the repository then gives the
     * account-global value precedence over this relationship's provisional declaration.
     *
     * <p>⚠️ DECISIONS ONLY — never call this from a projection. {@link #lockAndReadBirthYear} takes a
     * PESSIMISTIC_WRITE lock, and {@code toResponse} runs once per relationship inside
     * {@code list()}. Routing the DTO through here made a plain list take a row-level write lock on
     * every counterparty, in list order, which breaks the one-row invariant that lock's Javadoc
     * states and lets two concurrent listers with overlapping learners deadlock. Projections use
     * {@link #readEffectiveBirthYear}.
     */
    private Integer resolveEffectiveBirthYearForDecision(
            LinkedLearnerRelationshipEntity relationship
    ) {
        UUID learnerUserId = relationship.getLearnerUserId();
        lockAndReadBirthYear(learnerUserId);
        return readEffectiveBirthYear(relationship);
    }

    /**
     * Unlocked read for PROJECTION. Same precedence as the decision path — account-global first,
     * this relationship's provisional declaration second — but it takes no lock, because rendering
     * a connection list must not serialize against acceptance or birth-year correction.
     */
    private Integer readEffectiveBirthYear(LinkedLearnerRelationshipEntity relationship) {
        return provisionalBirthYearRepository.findEffectiveBirthYear(
                relationship.getId(), relationship.getLearnerUserId()).orElse(null);
    }

    private void persistBirthYear(UUID learnerUserId, int birthYear) {
        validateBirthYear(birthYear);
        // Targeted update rather than save() on a loaded entity: UserEntity has no @DynamicUpdate,
        // so writing a snapshot taken before a blocking lock wait would rewrite every column.
        userRepository.writeBirthYear(learnerUserId, birthYear, OffsetDateTime.now());
    }

    private void validateBirthYear(int birthYear) {
        int currentYear = Year.now().getValue();
        if (birthYear < MINIMUM_BIRTH_YEAR || birthYear > currentYear) {
            throw new InvalidLinkedLearnerBirthYearException();
        }
    }

    private void validateCorrectionBirthYear(int birthYear) {
        if (birthYear < MINIMUM_BIRTH_YEAR || birthYear > MAXIMUM_PLAUSIBLE_BIRTH_YEAR) {
            throw new InvalidLinkedLearnerBirthYearException();
        }
    }

    private void requireRecordedBirthYear(Integer birthYear) {
        if (birthYear == null) {
            throw new LinkedLearnerBirthYearCorrectionNotAllowedException();
        }
    }

    private List<LinkedLearnerRelationshipEntity> relationshipsPausedByCorrection(
            UUID learnerUserId,
            Integer currentBirthYear,
            int correctedBirthYear
    ) {
        if (currentBirthYear == null
                || correctedBirthYear <= currentBirthYear
                || !guardianConsentPolicy.requiresGuardianConsent(correctedBirthYear)) {
            return List.of();
        }
        return relationshipRepository
                .findByLearnerUserIdAndStatus(learnerUserId, LinkedLearnerStatus.ACCEPTED)
                .stream()
                .filter(relationship -> consentRepository.findByRelationshipId(relationship.getId()).isEmpty())
                .toList();
    }

    private List<LinkedLearnerResponse> listRelationships(UUID callerUserId) {
        // ⚠️ Terminal rows are retained only while recent. The window follows request-ttl-days
        // deliberately, mirroring the invitation list's rule that retention uses the same configured
        // duration the thing was live for rather than a second hardcoded number — so do not replace
        // this with a literal 30 to "decouple" it.
        OffsetDateTime terminalCutoff = OffsetDateTime.now()
                .minusDays(properties.getLinkedLearners().getRequestTtlDays());
        List<LinkedLearnerRelationshipEntity> relationships = relationshipRepository
                .findVisibleForUser(callerUserId, terminalCutoff);
        Set<UUID> relationshipIds = relationships.stream()
                .map(LinkedLearnerRelationshipEntity::getId)
                .collect(Collectors.toSet());
        Map<UUID, List<LinkedLearnerGrantEntity>> grantsByRelationship = relationshipIds.isEmpty()
                ? Map.of()
                : grantRepository.findByRelationshipIdInAndScopeInAndRevokedAtIsNull(
                                relationshipIds, List.of(
                                        LinkedLearnerGrantScope.ACTIVITY,
                                        LinkedLearnerGrantScope.PROGRESS))
                        .stream()
                        .collect(Collectors.groupingBy(
                                LinkedLearnerGrantEntity::getRelationshipId
                        ));
        return relationships.stream()
                .map(relationship -> toResponse(
                        relationship,
                        callerUserId,
                        grantsByRelationship.getOrDefault(relationship.getId(), List.of())))
                .toList();
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
        List<LinkedLearnerGrantEntity> grants = grantRepository
                .findByRelationshipIdInAndScopeInAndRevokedAtIsNull(
                        Set.of(relationship.getId()), List.of(
                                LinkedLearnerGrantScope.ACTIVITY,
                                LinkedLearnerGrantScope.PROGRESS));
        return toResponse(relationship, callerUserId, grants);
    }

    private LinkedLearnerResponse toResponse(
            LinkedLearnerRelationshipEntity relationship,
            UUID callerUserId,
            List<LinkedLearnerGrantEntity> grants
    ) {
        LinkedLearnerSide callerRole = callerUserId.equals(relationship.getSupporterUserId())
                ? LinkedLearnerSide.SUPPORTER : LinkedLearnerSide.LEARNER;
        requireParty(relationship, callerUserId);
        UUID counterpartyId = callerRole == LinkedLearnerSide.SUPPORTER
                ? relationship.getLearnerUserId() : relationship.getSupporterUserId();
        UserEntity counterparty = requireUser(counterpartyId);
        UserEntity learner = callerRole == LinkedLearnerSide.LEARNER ? requireUser(callerUserId) : counterparty;
        Integer learnerBirthYear = readEffectiveBirthYear(relationship);
        boolean consentRequired = learnerBirthYear != null
                && guardianConsentPolicy.requiresGuardianConsent(learnerBirthYear);
        boolean consentRecorded = consentRepository.findByRelationshipId(relationship.getId()).isPresent();
        boolean invitedParty = relationship.getInitiatedBy() != callerRole;
        boolean accepted = relationship.getStatus() == LinkedLearnerStatus.ACCEPTED;
        Set<UUID> activitySharedFromUserIds = sharedFromUserIds(grants, LinkedLearnerGrantScope.ACTIVITY);
        Set<UUID> progressSharedFromUserIds = sharedFromUserIds(grants, LinkedLearnerGrantScope.PROGRESS);
        // ⚠️ Mirror requireGrant's consent gate, INCLUDING its asymmetry. Guardian consent protects
        // the LEARNER's data only, so it blocks a read whose counterparty is the learner and must NOT
        // touch a read of the supporter's own shared activity — blanket-applying it would wrongly
        // hide a supporter's activity from a learner who happens to require consent.
        //
        // Without this the DTO was the MORE PERMISSIVE of the two: `requireGrant` denies on missing
        // consent while `*SharedWithMe` did not, so a supporter could be shown a "View progress" link
        // whose read then 404s — with no way back, because recordGuardianConsent requires PENDING and
        // an ACCEPTED relationship in that state can only be repaired by revoke and re-invite.
        // Reachable today only by raising GUARDIAN_CONSENT_MAX_AGE, which is owner-owned and pending
        // counsel — planned rather than hypothetical, which is why this is defence in depth and not
        // dead code.
        boolean counterpartyIsLearner = callerRole == LinkedLearnerSide.SUPPORTER;
        // ⚠️ An UNKNOWN birth year withholds too, matching requireGrant's deny-on-null branch. Without
        // this the DTO failed OPEN exactly where the check fails CLOSED — the stale-permissive shape
        // this predicate exists to remove — because `consentRequired` is false when the year is null.
        // Found by the v0.94.0 cold-agent pressure test, in code added by v0.94.0 item 5 whose own
        // comment claimed to mirror that gate.
        boolean learnerAgeUnknown = counterpartyIsLearner && learnerBirthYear == null;
        boolean counterpartyDataWithheldForConsent =
                learnerAgeUnknown || (counterpartyIsLearner && consentRequired && !consentRecorded);
        boolean readableFromCounterparty = accepted && !counterpartyDataWithheldForConsent;

        return new LinkedLearnerResponse(
                relationship.getId(),
                callerRole,
                relationship.getInitiatedBy(),
                relationship.getStatus() == LinkedLearnerStatus.PENDING && invitedParty,
                // Email invitations still create no relationship row until their recipient accepts.
                // Shareable-link redemption is the deliberate exception: it creates PENDING so the
                // creator can identify and confirm the redeemer. That is the same display name the
                // authenticated resolve endpoint already discloses; email remains accepted-only below.
                // Gating the name on acceptedAt would also blank consent-pending and re-paused links.
                resolveDisplayName(counterparty),
                // A shareable-link redemption creates PENDING before the creator confirms. Email
                // must not leak through the relationship list after resolve deliberately withheld
                // it, so identity stays display-name-only until mutual agreement is complete.
                accepted ? counterparty.getEmail() : null,
                relationship.getStatus(),
                relationship.getCreatedAt(),
                relationship.getAcceptedAt(),
                relationship.getRevokedAt(),
                relationship.getExpiresAt(),
                learnerBirthYear == null,
                consentRequired,
                consentRecorded,
                activitySharedFromUserIds.contains(callerUserId),
                readableFromCounterparty && activitySharedFromUserIds.contains(counterpartyId),
                progressSharedFromUserIds.contains(callerUserId),
                readableFromCounterparty && progressSharedFromUserIds.contains(counterpartyId)
        );
    }

    private Set<UUID> sharedFromUserIds(
            List<LinkedLearnerGrantEntity> grants,
            LinkedLearnerGrantScope scope
    ) {
        return grants.stream()
                .filter(grant -> grant.getScope() == scope)
                .map(LinkedLearnerGrantEntity::getFromUserId)
                .collect(Collectors.toSet());
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
