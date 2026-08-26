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

/**
 * An invitation keyed on the typed email address rather than a resolved user id.
 *
 * <p>⚠️ This exists so the invite endpoint cannot leak account existence: a row is written whether
 * or not the address belongs to an account, so there is no observable branch. It also lets someone
 * invite a person who has not signed up yet.
 *
 * <p>⚠️ An invitation is NOT a relationship. {@code LinkedLearnerRelationshipEntity} rows are
 * created only on acceptance, which keeps {@code linked_learner_relationships} meaning what the
 * open checkpoint counts.
 */
@Entity
@Table(name = "linked_learner_invitations")
@Getter
@Setter
public class LinkedLearnerInvitationEntity {
    @Id
    private UUID id;

    @Column(name = "inviter_user_id", nullable = false)
    private UUID inviterUserId;

    @Column(name = "invited_email", nullable = false, length = 320)
    private String invitedEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "inviter_role", nullable = false, length = 16)
    private LinkedLearnerSide inviterRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LinkedLearnerStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;
}
