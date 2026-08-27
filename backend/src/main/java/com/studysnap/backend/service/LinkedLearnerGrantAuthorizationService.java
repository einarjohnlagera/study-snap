package com.studysnap.backend.service;

import com.studysnap.backend.entity.LinkedLearnerGrantEntity;
import com.studysnap.backend.entity.LinkedLearnerGrantScope;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.LinkedLearnerNotFoundException;
import com.studysnap.backend.repository.LinkedLearnerGrantRepository;
import com.studysnap.backend.repository.LinkedLearnerGuardianConsentRepository;
import com.studysnap.backend.repository.LinkedLearnerRelationshipRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LinkedLearnerGrantAuthorizationService {
    private final LinkedLearnerRelationshipRepository relationshipRepository;
    private final LinkedLearnerGrantRepository grantRepository;
    private final LinkedLearnerGuardianConsentRepository consentRepository;
    private final UserRepository userRepository;
    private final GuardianConsentPolicy guardianConsentPolicy;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public UUID requireGrant(UUID callerUserId, UUID relationshipId, LinkedLearnerGrantScope scope) {
        LinkedLearnerRelationshipEntity relationship = relationshipRepository.findById(relationshipId)
                .orElseThrow(LinkedLearnerNotFoundException::new);
        if (relationship.getStatus() != LinkedLearnerStatus.ACCEPTED) {
            throw new LinkedLearnerNotFoundException();
        }

        UUID fromUserId = resolveOtherParty(relationship, callerUserId);
        LinkedLearnerGrantEntity grant = grantRepository
                .findFirstByRelationshipIdAndFromUserIdAndScopeAndRevokedAtIsNull(
                        relationshipId, fromUserId, scope)
                .filter(candidate -> callerUserId.equals(candidate.getToUserId()))
                .orElseThrow(LinkedLearnerNotFoundException::new);

        // ⚠️ Guardian consent is ASYMMETRIC: it gates the LEARNER's data only. A supporter sharing
        // their own activity with a learner who requires consent is not gated by it.
        if (fromUserId.equals(relationship.getLearnerUserId())) {
            UserEntity learner = userRepository.findById(fromUserId)
                    .orElseThrow(LinkedLearnerNotFoundException::new);
            Integer birthYear = learner.getBirthYear();
            // ⚠️ A NULL birth year DENIES. This branch is defence in depth — acceptance records the
            // learner's year, so an ACCEPTED relationship always carries one today and this cannot
            // deny anybody. That is exactly why it must not fail open: the only way to reach here
            // with a null year is a future grant path that produced ACCEPTED without one, which is
            // the very state this check exists to catch. Treating "unknown age" as "no consent
            // needed" would let that path silently reopen `v0.89.1`'s gate.
            if (birthYear == null
                    || (guardianConsentPolicy.requiresGuardianConsent(birthYear)
                            && consentRepository.findByRelationshipId(relationshipId).isEmpty())) {
                throw new LinkedLearnerNotFoundException();
            }
        }

        // Defence in depth: accepted relationships are verified-email gated today, but keeping
        // this check on every grant read prevents a future grant path from silently opening access.
        authService.requireEmailVerified(callerUserId);
        return fromUserId;
    }

    private UUID resolveOtherParty(LinkedLearnerRelationshipEntity relationship, UUID callerUserId) {
        if (callerUserId.equals(relationship.getSupporterUserId())) {
            return relationship.getLearnerUserId();
        }
        if (callerUserId.equals(relationship.getLearnerUserId())) {
            return relationship.getSupporterUserId();
        }
        throw new LinkedLearnerNotFoundException();
    }
}
