package com.studysnap.backend.repository;

import com.studysnap.backend.entity.LinkedLearnerProvisionalBirthYearEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
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
     *
     * <p>⚠️ The relationship join is a STRUCTURAL guard, not decoration. Without it the provisional
     * row was reached by {@code relationship_id} alone while the account row was selected by a
     * separately-passed {@code learnerUserId}, with nothing tying the two together — so a caller
     * passing a mismatched pair would coalesce a STRANGER'S declared birth year onto this user's
     * consent decision. Every current caller passes a matched pair, which is exactly why the
     * mismatch would never announce itself. Joining through the relationship makes the fallback
     * unreachable for a pair that does not belong together, rather than merely unused.
     */
    @Query(value = """
            select coalesce(u.birth_year, p.birth_year)
              from users u
              left join linked_learner_relationships r
                on r.id = :relationshipId
               and r.learner_user_id = u.id
              left join linked_learner_provisional_birth_years p
                on p.relationship_id = r.id
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

    /**
     * Every provisional declaration this user made AS THE LEARNER, for the account data export.
     *
     * <p>⚠️ THE JOIN THROUGH THE RELATIONSHIP IS THE PRIVACY BOUNDARY, and it is the same structural
     * guard {@link #findEffectiveBirthYear} documents. This table is keyed by RELATIONSHIP, not by
     * user, so there is no user column to filter on: reaching rows by any path that does not join
     * {@code linked_learner_relationships} on {@code learner_user_id} would export a declaration
     * belonging to somebody else. Filtering in Java after a broader read is the same defect with a
     * later boundary — the read itself must be unable to see another learner's row.
     *
     * <p>⚠️ A LEARNER CAN HOLD MORE THAN ONE. The primary key is {@code relationship_id}, and the
     * insert is guarded only on the relationship being PENDING and the account year being absent, so
     * a learner who redeems two links before either creator confirms has two independent
     * declarations with their own {@code declared_at}. The export returns all of them; a single
     * scalar would silently drop one.
     */
    @Query(value = """
            select p.*
              from linked_learner_provisional_birth_years p
              join linked_learner_relationships r
                on r.id = p.relationship_id
             where r.learner_user_id = :learnerUserId
             order by p.declared_at, p.relationship_id
            """, nativeQuery = true)
    List<LinkedLearnerProvisionalBirthYearEntity> findAllDeclaredByLearner(
            @Param("learnerUserId") UUID learnerUserId
    );

    /**
     * Delete every provisional declaration this learner holds, once their account-global year exists.
     *
     * <p>⚠️ WHY ALL OF THEM, not just the promoted relationship's. A learner can hold more than one
     * declaration — the primary key is {@code relationship_id} and the insert is guarded only on the
     * relationship being PENDING with a null account year, so redeeming two links before either
     * creator confirms produces two rows. Deleting only the promoted one leaves the sibling behind
     * **after the account-global value already exists**, which is a retained declared-value history
     * and is exactly what v0.89.1 forbids.
     *
     * <p>⚠️ THE SIBLING IS ALREADY INERT, which is why this is a retention fix and not a behaviour
     * change: {@link #findEffectiveBirthYear} coalesces {@code users.birth_year} FIRST, so once the
     * account column is written no provisional row can affect consent, acceptance or the export.
     *
     * <p>⚠️ SCOPED THROUGH THE RELATIONSHIP JOIN, never by a bare user id — the same structural guard
     * every other statement in this class uses, because the table has no user column of its own.
     *
     * <p>⚠️ Guarded on the account year being PRESENT. Calling it before promotion would delete a
     * declaration that is still load-bearing, so the guard makes the ordering impossible to get
     * wrong rather than merely documented.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            delete from linked_learner_provisional_birth_years p
             using linked_learner_relationships r, users u
             where r.id = p.relationship_id
               and u.id = r.learner_user_id
               and u.id = :learnerUserId
               and u.birth_year is not null
            """, nativeQuery = true)
    int deleteAllForLearnerOncePromoted(@Param("learnerUserId") UUID learnerUserId);

    /** Scoped and idempotent cleanup; provisional declarations are not retained as history. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            delete from linked_learner_provisional_birth_years
             where relationship_id = :relationshipId
            """, nativeQuery = true)
    int deleteForRelationship(@Param("relationshipId") UUID relationshipId);
}
