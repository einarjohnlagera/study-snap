package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "linked_learner_invitation_links")
@Getter
@Setter
public class LinkedLearnerInvitationLinkEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 22)
    private String token;

    @Column(name = "creator_user_id", nullable = false)
    private UUID creatorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "creator_role", nullable = false, length = 16)
    private LinkedLearnerSide creatorRole;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "redeemed_at")
    private OffsetDateTime redeemedAt;

    @Column(name = "redeemed_by_user_id")
    private UUID redeemedByUserId;
}

