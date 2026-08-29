package com.studysnap.backend.repository;

import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinkedLearnerRelationshipRepository extends JpaRepository<LinkedLearnerRelationshipEntity, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO linked_learner_relationships
                (id, supporter_user_id, learner_user_id, status, initiated_by, created_at, expires_at)
            VALUES (:id, :supporterUserId, :learnerUserId, 'PENDING', :initiatedBy, :createdAt, :expiresAt)
            ON CONFLICT (supporter_user_id, learner_user_id)
                WHERE status IN ('PENDING', 'ACCEPTED')
            DO NOTHING
            """, nativeQuery = true)
    int insertPendingIfAbsent(
            @Param("id") UUID id,
            @Param("supporterUserId") UUID supporterUserId,
            @Param("learnerUserId") UUID learnerUserId,
            @Param("initiatedBy") String initiatedBy,
            @Param("createdAt") OffsetDateTime createdAt,
            @Param("expiresAt") OffsetDateTime expiresAt
    );

    @Query(value = """
            select id
              from linked_learner_relationships
             where status = 'PENDING'
               and expires_at <= :now
             order by expires_at, id
            """, nativeQuery = true)
    List<UUID> findDuePendingIds(@Param("now") OffsetDateTime now);

    Optional<LinkedLearnerRelationshipEntity> findFirstBySupporterUserIdAndLearnerUserIdAndStatusIn(
            UUID supporterUserId,
            UUID learnerUserId,
            Collection<LinkedLearnerStatus> statuses
    );

    List<LinkedLearnerRelationshipEntity> findBySupporterUserIdOrLearnerUserIdOrderByCreatedAtDesc(
            UUID supporterUserId,
            UUID learnerUserId
    );

    List<LinkedLearnerRelationshipEntity> findByLearnerUserIdAndStatus(
            UUID learnerUserId,
            LinkedLearnerStatus status
    );

    /**
     * Status transitions as CONDITIONAL updates. Read-modify-save loses a decision when two
     * requests observe the same row: an accept racing a revoke would both see PENDING, and
     * whichever flushed last would win — so a revoked connection could be resurrected as ACCEPTED,
     * with a live cross-user read behind it.
     *
     * <p>⚠️ {@code clearAutomatically} is required, not decorative: the caller holds this row in the
     * persistence context at its OLD status, and status is precisely what these change. Without the
     * clear, a re-read inside the same transaction returns the stale cached entity and the caller
     * reports an outcome that never happened. {@code flushAutomatically} keeps pending writes
     * (a birth year persisted moments earlier) from being discarded by that clear.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update linked_learner_relationships
               set status = 'ACCEPTED', accepted_at = :acceptedAt, revoked_at = null, expires_at = null
             where id = :id and status = 'PENDING'
            """, nativeQuery = true)
    int markAcceptedIfPending(@Param("id") UUID id, @Param("acceptedAt") OffsetDateTime acceptedAt);

    /**
     * Expiry is terminal and conditional. A zero count means accept/revoke won while this id was
     * queued; callers must retain the provisional row in that case.
     *
     * <p>⚠️ THE DEADLINE PREDICATE IS LOAD-BEARING, NOT A DUPLICATE OF THE FINDER'S. Selection and
     * execution are deliberately separate transactions (one learner lock each), so anything the
     * finder filtered can change in between. Guarding on {@code status = 'PENDING'} alone expired a
     * CONSENT-PAUSED relationship: {@code pauseAcceptedForConsent} returns an ACCEPTED row to
     * PENDING for a v0.89.1 birth-year correction and leaves {@code expires_at} NULL, because
     * acceptance cleared it — so the row looked exactly like an unconfirmed request and the sweep
     * terminated a connection that had already been confirmed. A pause is not a termination.
     * Re-checking the deadline here makes a NULL deadline structurally unexpirable rather than
     * merely unselected.
     *
     * <p>⚠️ {@code expires_at} is NOT overwritten with the sweep time. It means "the deadline", for
     * every status, and three dated checkpoints read this table — a column that silently changes
     * meaning on one status is the drift those reads cannot survive.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update linked_learner_relationships
               set status = 'EXPIRED'
             where id = :id
               and status = 'PENDING'
               and expires_at is not null
               and expires_at <= :expiredAt
            """, nativeQuery = true)
    int markExpiredIfPending(@Param("id") UUID id, @Param("expiredAt") OffsetDateTime expiredAt);

    /**
     * ⚠️ Guarded on BOTH live statuses, not just PENDING. Revocation has to win even when an accept
     * committed first — otherwise "revocation cuts the read immediately" is false for exactly the
     * interleaving that matters.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update linked_learner_relationships
               set status = 'REVOKED', revoked_at = :revokedAt
             where id = :id and status in ('PENDING', 'ACCEPTED')
            """, nativeQuery = true)
    int markRevokedIfLive(@Param("id") UUID id, @Param("revokedAt") OffsetDateTime revokedAt);

    /**
     * Pause an accepted connection that a birth-year correction has made consent-requiring.
     * Conditional on ACCEPTED so a revoke committing between the select and this write is not
     * overwritten back to PENDING, which would resurrect a relationship the learner just ended.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update linked_learner_relationships
               set status = 'PENDING', accepted_at = null
             where id = :id and status = 'ACCEPTED'
            """, nativeQuery = true)
    int pauseAcceptedForConsent(@Param("id") UUID id);
}
