package com.studysnap.backend.repository;

import com.studysnap.backend.entity.LinkedLearnerInvitationEntity;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinkedLearnerInvitationRepository extends JpaRepository<LinkedLearnerInvitationEntity, UUID> {

    /**
     * Atomic upsert so two concurrent invites to the same address cannot create two live rows.
     * ON CONFLICT DO NOTHING against the partial unique index, mirroring the relationship insert.
     */
    @Modifying
    @Query(value = """
            insert into linked_learner_invitations
                (id, inviter_user_id, invited_email, inviter_role, status, created_at)
            values (:id, :inviterUserId, :invitedEmail, :inviterRole, 'PENDING', :createdAt)
            on conflict do nothing
            """, nativeQuery = true)
    void insertPendingIfAbsent(
            @Param("id") UUID id,
            @Param("inviterUserId") UUID inviterUserId,
            @Param("invitedEmail") String invitedEmail,
            @Param("inviterRole") String inviterRole,
            @Param("createdAt") OffsetDateTime createdAt
    );

    Optional<LinkedLearnerInvitationEntity> findFirstByInviterUserIdAndInvitedEmailAndStatus(
            UUID inviterUserId, String invitedEmail, LinkedLearnerStatus status);

    List<LinkedLearnerInvitationEntity> findByInviterUserIdAndStatus(UUID inviterUserId, LinkedLearnerStatus status);

    /** Incoming lookup by address, so an invitation can predate the recipient's account. */
    List<LinkedLearnerInvitationEntity> findByInvitedEmailAndStatus(String invitedEmail, LinkedLearnerStatus status);
}
