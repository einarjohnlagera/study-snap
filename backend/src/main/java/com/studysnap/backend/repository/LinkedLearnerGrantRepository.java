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

    @Modifying
    @Query(value = """
            INSERT INTO linked_learner_grants
                (id, relationship_id, from_user_id, to_user_id, scope, granted_at)
            VALUES (:id, :relationshipId, :fromUserId, :toUserId, :scope, :grantedAt)
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
}
