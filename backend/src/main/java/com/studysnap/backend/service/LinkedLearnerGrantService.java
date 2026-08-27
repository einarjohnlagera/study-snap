package com.studysnap.backend.service;

import com.studysnap.backend.dto.LinkedLearnerActivityGrantResponse;
import com.studysnap.backend.entity.LinkedLearnerGrantScope;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.exception.LinkedLearnerNotFoundException;
import com.studysnap.backend.repository.LinkedLearnerGrantRepository;
import com.studysnap.backend.repository.LinkedLearnerRelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LinkedLearnerGrantService {
    private final LinkedLearnerRelationshipRepository relationshipRepository;
    private final LinkedLearnerGrantRepository grantRepository;
    private final AuthService authService;

    @Transactional
    public LinkedLearnerActivityGrantResponse setActivityGrant(
            UUID callerUserId,
            UUID relationshipId,
            boolean granted
    ) {
        authService.requireEmailVerified(callerUserId);
        LinkedLearnerRelationshipEntity relationship = relationshipRepository.findById(relationshipId)
                .orElseThrow(LinkedLearnerNotFoundException::new);
        if (relationship.getStatus() != LinkedLearnerStatus.ACCEPTED) {
            throw new LinkedLearnerNotFoundException();
        }
        UUID toUserId = resolveOtherParty(relationship, callerUserId);

        if (granted) {
            grantRepository.insertLiveIfAbsent(
                    UUID.randomUUID(), relationshipId, callerUserId, toUserId,
                    LinkedLearnerGrantScope.ACTIVITY.name(), OffsetDateTime.now(ZoneOffset.UTC));
        } else {
            grantRepository.revokeLive(
                    relationshipId, callerUserId, LinkedLearnerGrantScope.ACTIVITY,
                    OffsetDateTime.now(ZoneOffset.UTC));
        }
        return new LinkedLearnerActivityGrantResponse(granted);
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
