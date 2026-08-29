package com.studysnap.backend.repository;

import com.studysnap.backend.entity.LinkedLearnerGrantEntity;
import com.studysnap.backend.entity.LinkedLearnerGrantScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinkedLearnerGrantRepository extends JpaRepository<LinkedLearnerGrantEntity, UUID> {
    Optional<LinkedLearnerGrantEntity> findFirstByRelationshipIdAndFromUserIdAndScopeAndRevokedAtIsNull(
            UUID relationshipId,
            UUID fromUserId,
            LinkedLearnerGrantScope scope
    );

    List<LinkedLearnerGrantEntity> findByRelationshipIdInAndScopeAndRevokedAtIsNull(
            Collection<UUID> relationshipIds,
            LinkedLearnerGrantScope scope
    );

    List<LinkedLearnerGrantEntity> findByRelationshipIdInAndScopeInAndRevokedAtIsNull(
            Collection<UUID> relationshipIds,
            Collection<LinkedLearnerGrantScope> scopes
    );

    @Modifying
    @Query(value = """
            INSERT INTO linked_learner_grants
                (id, relationship_id, from_user_id, to_user_id, scope, granted_at)
            SELECT :id, :relationshipId, :fromUserId, :toUserId, :scope, :grantedAt
              FROM linked_learner_relationships relationship
             WHERE relationship.id = :relationshipId
               AND relationship.status = 'ACCEPTED'
            ON CONFLICT (relationship_id, from_user_id, scope)
                WHERE revoked_at IS NULL
            DO NOTHING
            """, nativeQuery = true)
    int insertLiveIfAbsent(
            @Param("id") UUID id,
            @Param("relationshipId") UUID relationshipId,
            @Param("fromUserId") UUID fromUserId,
            @Param("toUserId") UUID toUserId,
            @Param("scope") String scope,
            @Param("grantedAt") OffsetDateTime grantedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LinkedLearnerGrantEntity grant
               set grant.revokedAt = :revokedAt
             where grant.relationshipId = :relationshipId
               and grant.fromUserId = :fromUserId
               and grant.scope = :scope
               and grant.revokedAt is null
            """)
    int revokeLive(
            @Param("relationshipId") UUID relationshipId,
            @Param("fromUserId") UUID fromUserId,
            @Param("scope") LinkedLearnerGrantScope scope,
            @Param("revokedAt") OffsetDateTime revokedAt
    );

    /**
     * Cut every live grant on a relationship, in both directions and every scope, for a TERMINAL
     * transition.
     *
     * <p>⚠️ THIS IS FOR TERMINAL STATUSES ONLY — {@code REVOKED} and {@code EXPIRED}. It must NEVER
     * be called on the {@code ACCEPTED -> PENDING} consent pause. v0.93.0 made the grant row survive
     * that pause BY DESIGN: {@code *SharedByMe} reflects the ROW, so it reports the caller's own
     * standing act of sharing and what will resume on re-acceptance. Cutting on a pause would make a
     * learner's own toggle read OFF while they never touched it, and sharing would not resume.
     *
     * <p>⚠️ Unlike {@link #revokeLive} this is deliberately NOT scoped to a direction or a scope. A
     * terminated relationship ends every grant on it, whoever issued it; leaving one live would keep
     * {@code *SharedByMe} asserting a sharing act on a relationship that no longer exists.
     *
     * <p>Idempotent via the {@code revokedAt is null} guard, so calling it on an already-terminal
     * relationship is a no-op — which is also what heals rows left live by the pre-v0.97.0 revoke.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LinkedLearnerGrantEntity grant
               set grant.revokedAt = :revokedAt
             where grant.relationshipId = :relationshipId
               and grant.revokedAt is null
            """)
    int revokeAllLiveForRelationship(
            @Param("relationshipId") UUID relationshipId,
            @Param("revokedAt") OffsetDateTime revokedAt
    );
}
