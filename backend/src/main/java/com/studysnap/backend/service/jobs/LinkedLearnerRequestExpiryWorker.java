package com.studysnap.backend.service.jobs;

import com.studysnap.backend.repository.LinkedLearnerGrantRepository;
import com.studysnap.backend.repository.LinkedLearnerProvisionalBirthYearRepository;
import com.studysnap.backend.repository.LinkedLearnerRelationshipRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Expires one relationship per transaction.
 *
 * <p>The learner row lock is the same ordering barrier used by acceptance, revocation and birth-year
 * correction. Keeping this worker in a separate bean makes {@link Transactional} effective when the
 * non-transactional scheduler invokes it, while ensuring a sweep never holds multiple learner locks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LinkedLearnerRequestExpiryWorker {
    private final LinkedLearnerRelationshipRepository relationshipRepository;
    private final LinkedLearnerProvisionalBirthYearRepository provisionalBirthYearRepository;
    private final LinkedLearnerGrantRepository grantRepository;
    private final UserRepository userRepository;

    @Transactional
    public boolean expire(UUID relationshipId, OffsetDateTime expiredAt) {
        return relationshipRepository.findById(relationshipId)
                .map(relationship -> {
                    // Lock for ordering, not for the value. Exactly one learner row is locked in
                    // this transaction, preserving the no-cycle argument used by the other writers.
                    // ⚠️ A learner deleted between the job's read and this lock is a BENIGN race —
                    // account deletion cascades the relationship away, so there is nothing left to
                    // expire. A bare orElseThrow raised NoSuchElementException and the job logged it
                    // at error, which turns an expected outcome into noise that hides real failures.
                    if (userRepository.findByIdForUpdate(relationship.getLearnerUserId()).isEmpty()) {
                        log.debug("linked-learners.request-expiry skipped relationshipId={} reason=learner-gone",
                                relationshipId);
                        return false;
                    }
                    if (relationshipRepository.markExpiredIfPending(relationshipId, expiredAt) == 0) {
                        // Accept, revoke or correction may have changed the row after due-id selection.
                        // Its provisional declaration belongs to that winning state and must survive.
                        log.debug("linked-learners.request-expiry skipped relationshipId={} reason=not-pending",
                                relationshipId);
                        return false;
                    }
                    // ⚠️ Same terminal rule as revoke: every live grant on this relationship ends,
                    // both directions and every scope. Inside the winning branch, so a relationship
                    // that was accepted or paused instead keeps its grants.
                    grantRepository.revokeAllLiveForRelationship(relationshipId, expiredAt);
                    provisionalBirthYearRepository.deleteForRelationship(relationshipId);
                    log.info("linked-learners.request-expiry expired relationshipId={}", relationshipId);
                    return true;
                })
                .orElseGet(() -> {
                    log.debug("linked-learners.request-expiry skipped relationshipId={} reason=not-found",
                            relationshipId);
                    return false;
                });
    }
}
