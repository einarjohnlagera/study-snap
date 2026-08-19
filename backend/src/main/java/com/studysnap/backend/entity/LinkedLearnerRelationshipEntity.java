package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "linked_learner_relationships")
@Getter
@Setter
@NoArgsConstructor
public class LinkedLearnerRelationshipEntity {
    @Id
    private UUID id;

    @Column(name = "supporter_user_id", nullable = false)
    private UUID supporterUserId;

    @Column(name = "learner_user_id", nullable = false)
    private UUID learnerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LinkedLearnerStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "initiated_by", nullable = false, length = 16)
    private LinkedLearnerSide initiatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;
}
