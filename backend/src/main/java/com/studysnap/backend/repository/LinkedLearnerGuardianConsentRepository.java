package com.studysnap.backend.repository;

import com.studysnap.backend.entity.LinkedLearnerGuardianConsentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LinkedLearnerGuardianConsentRepository extends JpaRepository<LinkedLearnerGuardianConsentEntity, UUID> {
    Optional<LinkedLearnerGuardianConsentEntity> findByRelationshipId(UUID relationshipId);
}
