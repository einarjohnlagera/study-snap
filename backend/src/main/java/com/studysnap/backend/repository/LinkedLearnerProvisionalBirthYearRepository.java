package com.studysnap.backend.repository;

import com.studysnap.backend.entity.LinkedLearnerProvisionalBirthYearEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface LinkedLearnerProvisionalBirthYearRepository
        extends JpaRepository<LinkedLearnerProvisionalBirthYearEntity, UUID> {

    /**
     * Write only for this relationship's learner, while the relationship is still pending and the
     * account-global year is absent. The service already holds the learner row lock; keeping these
     * predicates in the statement makes the privacy boundary fail closed if a caller ever drifts.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into linked_learner_provisional_birth_years
                (relationship_id, birth_year, declared_at)
            select r.id, :birthYear, :declaredAt
              from linked_learner_relationships r
              join users u on u.id = r.learner_user_id
             where r.id = :relationshipId
               and r.learner_user_id = :learnerUserId
               and r.status = 'PENDING'
               and u.birth_year is null
            on conflict (relationship_id) do nothing
            """, nativeQuery = true)
    int insertIfAccountBirthYearMissing(
            @Param("relationshipId") UUID relationshipId,
            @Param("learnerUserId") UUID learnerUserId,
            @Param("birthYear") int birthYear,
            @Param("declaredAt") OffsetDateTime declaredAt
    );

    /**
     * The account-global value always wins. Callers acquire the learner row lock and perform its
     * separate scalar read before invoking this statement; see LinkedLearnerService's lock Javadoc.
     */
    @Query(value = """
            select coalesce(u.birth_year, p.birth_year)
              from users u
              left join linked_learner_provisional_birth_years p
                on p.relationship_id = :relationshipId
             where u.id = :learnerUserId
            """, nativeQuery = true)
    Optional<Integer> findEffectiveBirthYear(
            @Param("relationshipId") UUID relationshipId,
            @Param("learnerUserId") UUID learnerUserId
    );

    /** Promote only after this exact relationship has reached ACCEPTED, and never overwrite. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update users u
               set birth_year = p.birth_year,
                   birth_year_updated_at = :updatedAt,
                   updated_at = :updatedAt
              from linked_learner_provisional_birth_years p,
                   linked_learner_relationships r
             where u.id = :learnerUserId
               and u.birth_year is null
               and p.relationship_id = :relationshipId
               and r.id = p.relationship_id
               and r.learner_user_id = u.id
               and r.status = 'ACCEPTED'
            """, nativeQuery = true)
    int promoteIfAccountBirthYearMissing(
            @Param("relationshipId") UUID relationshipId,
            @Param("learnerUserId") UUID learnerUserId,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    /** Scoped and idempotent cleanup; provisional declarations are not retained as history. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            delete from linked_learner_provisional_birth_years
             where relationship_id = :relationshipId
            """, nativeQuery = true)
    int deleteForRelationship(@Param("relationshipId") UUID relationshipId);
}
