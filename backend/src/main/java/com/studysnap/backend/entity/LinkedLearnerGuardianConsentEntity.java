package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "linked_learner_guardian_consents")
@Getter
@Setter
@NoArgsConstructor
public class LinkedLearnerGuardianConsentEntity {
    @Id
    private UUID id;

    @Column(name = "relationship_id", nullable = false, unique = true)
    private UUID relationshipId;

    @Column(name = "learner_user_id", nullable = false)
    private UUID learnerUserId;

    @Column(name = "attested_by_user_id", nullable = false)
    private UUID attestedByUserId;

    @Column(name = "attested_at", nullable = false)
    private OffsetDateTime attestedAt;

    @Column(name = "attestation_version", nullable = false, length = 64)
    private String attestationVersion;
}
