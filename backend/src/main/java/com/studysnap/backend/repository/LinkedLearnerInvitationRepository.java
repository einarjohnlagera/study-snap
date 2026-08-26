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
                (id, inviter_user_id, invited_email, inviter_role, status, created_at, expires_at)
            values (:id, :inviterUserId, :invitedEmail, :inviterRole, 'PENDING', :createdAt, :expiresAt)
            on conflict do nothing
            """, nativeQuery = true)
    void insertPendingIfAbsent(
            @Param("id") UUID id,
            @Param("inviterUserId") UUID inviterUserId,
            @Param("invitedEmail") String invitedEmail,
            @Param("inviterRole") String inviterRole,
            @Param("createdAt") OffsetDateTime createdAt,
            @Param("expiresAt") OffsetDateTime expiresAt
    );

    /**
     * Re-arm an invitation that lapsed, so an expired offer does not permanently block re-inviting
     * the same address through the partial unique index. createdAt is deliberately NOT touched: it
     * records when the address was first invited and the list displays it.
     *
     * <p>⚠️ The ROLE is re-applied from the new request, not preserved. insertPendingIfAbsent
     * no-ops against the live-row index, so without this an address first invited as SUPPORTER and
     * later re-invited as LEARNER would reactivate the OLD direction — and acceptance would then
     * build the opposite relationship to the one the inviter just asked for.
     *
     * <p>The {@code expires_at <= :now} guard is what keeps an UNEXPIRED duplicate idempotent:
     * repeating the endpoint on a live invitation re-sends mail and changes no stored row.
     */
    @Modifying
    @Query(value = """
            update linked_learner_invitations
               set expires_at = :expiresAt,
                   inviter_role = :inviterRole
             where inviter_user_id = :inviterUserId
               and invited_email = :invitedEmail
               and status = 'PENDING'
               and expires_at <= :now
            """, nativeQuery = true)
    int reArmExpired(
            @Param("inviterUserId") UUID inviterUserId,
            @Param("invitedEmail") String invitedEmail,
            @Param("inviterRole") String inviterRole,
            @Param("expiresAt") OffsetDateTime expiresAt,
            @Param("now") OffsetDateTime now
    );

    /**
     * Status transitions as CONDITIONAL updates rather than read-modify-write. Two callers racing
     * (an accept against a revoke) would otherwise both read PENDING and both write, losing one
     * decision — and the accept path is the one that grants a cross-user read, so the lost write
     * could leave an accepted relationship behind a revoked invitation. Returns rows affected;
     * 0 means somebody else moved it first.
     */
    @Modifying
    @Query(value = """
            update linked_learner_invitations
               set status = 'ACCEPTED', accepted_at = :acceptedAt
             where id = :id and status = 'PENDING' and expires_at > :now
            """, nativeQuery = true)
    int markAcceptedIfPending(
            @Param("id") UUID id,
            @Param("acceptedAt") OffsetDateTime acceptedAt,
            @Param("now") OffsetDateTime now
    );

    @Modifying
    @Query(value = """
            update linked_learner_invitations
               set status = 'REVOKED', revoked_at = :revokedAt
             where id = :id and status = 'PENDING'
            """, nativeQuery = true)
    int markRevokedIfPending(@Param("id") UUID id, @Param("revokedAt") OffsetDateTime revokedAt);

    Optional<LinkedLearnerInvitationEntity> findFirstByInviterUserIdAndInvitedEmailAndStatus(
            UUID inviterUserId, String invitedEmail, LinkedLearnerStatus status);

    List<LinkedLearnerInvitationEntity> findByInviterUserIdAndStatusAndExpiresAtAfter(
            UUID inviterUserId, LinkedLearnerStatus status, OffsetDateTime now);

    /**
     * Incoming lookup by address, so an invitation can predate the recipient's account.
     * ⚠️ Filters on expiry too. Rejecting an expired invitation only at accept time would leave it
     * listed and actionable in the recipient's UI, which is the same class of gap as gating one
     * entry point and missing its sibling.
     */
    List<LinkedLearnerInvitationEntity> findByInvitedEmailAndStatusAndExpiresAtAfter(
            String invitedEmail, LinkedLearnerStatus status, OffsetDateTime now);
}
