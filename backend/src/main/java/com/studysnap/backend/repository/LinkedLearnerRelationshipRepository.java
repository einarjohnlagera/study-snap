package com.studysnap.backend.repository;

import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinkedLearnerRelationshipRepository extends JpaRepository<LinkedLearnerRelationshipEntity, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO linked_learner_relationships
                (id, supporter_user_id, learner_user_id, status, initiated_by, created_at)
            VALUES (:id, :supporterUserId, :learnerUserId, 'PENDING', :initiatedBy, :createdAt)
            ON CONFLICT (supporter_user_id, learner_user_id)
                WHERE status IN ('PENDING', 'ACCEPTED')
            DO NOTHING
            """, nativeQuery = true)
    int insertPendingIfAbsent(
            @Param("id") UUID id,
            @Param("supporterUserId") UUID supporterUserId,
            @Param("learnerUserId") UUID learnerUserId,
            @Param("initiatedBy") String initiatedBy,
            @Param("createdAt") java.time.OffsetDateTime createdAt
    );

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
}
