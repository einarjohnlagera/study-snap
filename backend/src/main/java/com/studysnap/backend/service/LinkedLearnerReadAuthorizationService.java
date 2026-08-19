package com.studysnap.backend.service;

import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerStatus;
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

    @Transactional(readOnly = true)
    public UUID requireAcceptedLearnerId(UUID callerUserId, UUID relationshipId) {
        LinkedLearnerRelationshipEntity relationship = relationshipRepository.findById(relationshipId)
                .orElseThrow(LinkedLearnerProgressNotFoundException::new);
        if (!callerUserId.equals(relationship.getSupporterUserId())
                || relationship.getStatus() != LinkedLearnerStatus.ACCEPTED) {
            throw new LinkedLearnerProgressNotFoundException();
        }
        return relationship.getLearnerUserId();
    }
}
