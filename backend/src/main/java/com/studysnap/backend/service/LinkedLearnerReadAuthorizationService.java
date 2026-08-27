package com.studysnap.backend.service;

import com.studysnap.backend.entity.LinkedLearnerGrantScope;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.exception.LinkedLearnerNotFoundException;
import com.studysnap.backend.exception.LinkedLearnerProgressNotFoundException;
import com.studysnap.backend.repository.LinkedLearnerRelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LinkedLearnerReadAuthorizationService {
    private final LinkedLearnerRelationshipRepository relationshipRepository;
    private final LinkedLearnerGrantAuthorizationService grantAuthorizationService;

    @Transactional(readOnly = true)
    public UUID requireAcceptedLearnerId(UUID callerUserId, UUID relationshipId) {
        LinkedLearnerRelationshipEntity relationship = relationshipRepository.findById(relationshipId)
                .orElseThrow(LinkedLearnerProgressNotFoundException::new);
        // ⚠️ Explicit by design: requireGrant is symmetric and returns the other party. Without
        // this assertion a learner could read the supporter's progress through this unidirectional route.
        if (!callerUserId.equals(relationship.getSupporterUserId())) {
            throw new LinkedLearnerProgressNotFoundException();
        }
        try {
            UUID learnerUserId = grantAuthorizationService.requireGrant(
                    callerUserId, relationshipId, LinkedLearnerGrantScope.PROGRESS);
            if (!relationship.getLearnerUserId().equals(learnerUserId)) {
                throw new LinkedLearnerProgressNotFoundException();
            }
            return learnerUserId;
        } catch (LinkedLearnerNotFoundException notFound) {
            // Preserve the progress route's established error code while reusing the grant gate.
            throw new LinkedLearnerProgressNotFoundException();
        }
    }
}
