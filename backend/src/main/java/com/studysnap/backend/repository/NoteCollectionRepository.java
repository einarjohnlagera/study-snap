package com.studysnap.backend.repository;

import com.studysnap.backend.entity.CollectionVisibility;
import com.studysnap.backend.entity.NoteCollectionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteCollectionRepository extends JpaRepository<NoteCollectionEntity, UUID> {
    List<NoteCollectionEntity> findByOwnerUserIdOrderByUpdatedAtDesc(UUID ownerUserId);

    List<NoteCollectionEntity> findByOwnerUserIdAndParentCollectionIdIsNullOrderByUpdatedAtDesc(UUID ownerUserId);

    List<NoteCollectionEntity> findByParentCollectionIdIn(List<UUID> parentCollectionIds);

    @Query("""
            select collection
            from NoteCollectionEntity collection
            where collection.parentCollectionId = :parentCollectionId
              and collection.ownerUserId = :ownerUserId
            order by
              case when collection.siblingPosition is null then 1 else 0 end,
              collection.siblingPosition asc,
              collection.updatedAt desc
            """)
    List<NoteCollectionEntity> findOrderedChildrenByParentCollectionIdAndOwnerUserId(
            @Param("parentCollectionId") UUID parentCollectionId,
            @Param("ownerUserId") UUID ownerUserId
    );

    @Query("""
            select coalesce(max(collection.siblingPosition), -1)
            from NoteCollectionEntity collection
            where collection.parentCollectionId = :parentCollectionId
              and collection.ownerUserId = :ownerUserId
            """)
    int findMaxSiblingPosition(
            @Param("parentCollectionId") UUID parentCollectionId,
            @Param("ownerUserId") UUID ownerUserId
    );

    List<NoteCollectionEntity> findByOwnerUserId(UUID ownerUserId);

    void deleteByOwnerUserId(UUID ownerUserId);

    Optional<NoteCollectionEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select collection
            from NoteCollectionEntity collection
            where collection.id = :id
              and collection.ownerUserId = :ownerUserId
            """)
    Optional<NoteCollectionEntity> findByIdAndOwnerUserIdForUpdate(
            @Param("id") UUID id,
            @Param("ownerUserId") UUID ownerUserId
    );

    Optional<NoteCollectionEntity> findByIdAndVisibility(UUID id, CollectionVisibility visibility);

    List<NoteCollectionEntity> findByVisibilityOrderByUpdatedAtDesc(CollectionVisibility visibility);

    List<NoteCollectionEntity> findByVisibilityAndCourseProgramOrderByUpdatedAtDesc(
            CollectionVisibility visibility,
            String courseProgram
    );

    List<NoteCollectionEntity> findByVisibilityAndParentCollectionIdIsNullOrderByUpdatedAtDesc(
            CollectionVisibility visibility
    );

    List<NoteCollectionEntity> findByVisibilityAndCourseProgramAndParentCollectionIdIsNullOrderByUpdatedAtDesc(
            CollectionVisibility visibility,
            String courseProgram
    );

    Optional<NoteCollectionEntity> findByOwnerUserIdAndSourcePlanId(UUID ownerUserId, UUID sourcePlanId);

    /**
     * ⚠️ THE SELF-COPY EXCLUSION IS NOT OPTIONAL — a curator can adopt their OWN public Review Set
     * ({@code adopt()} has no owner guard, and the service comments treat self-copies as a real state),
     * so without this join the curator receives "This Review Set has been updated" for their own
     * publish. {@link #countAdoptionsByCollectionIds} already excludes self-copies the same way and is
     * the precedent this follows; the two must not drift apart.
     */
    @Query("""
            select adoption.ownerUserId as recipientUserId, adoption.id as adoptedCollectionId
            from NoteCollectionEntity adoption
            join NoteCollectionEntity source on source.id = adoption.sourcePlanId
            where adoption.sourcePlanId = :sourceCollectionId
              and adoption.ownerUserId <> source.ownerUserId
            """)
    List<ReviewSetUpdateRecipientProjection> findReviewSetUpdateRecipients(
            @Param("sourceCollectionId") UUID sourceCollectionId
    );

    long countByParentCollectionId(UUID parentCollectionId);

    long countByOwnerUserIdAndParentCollectionIdIsNull(UUID ownerUserId);

    @Query("""
            select collection.parentCollectionId as collectionId, count(collection.id) as childCount
            from NoteCollectionEntity collection
            where collection.parentCollectionId in :collectionIds
            group by collection.parentCollectionId
            """)
    List<NoteCollectionChildCountProjection> countChildrenByCollectionIds(@Param("collectionIds") List<UUID> collectionIds);

    @Query("""
            select adoption.sourcePlanId as collectionId, count(adoption.id) as adoptionCount
            from NoteCollectionEntity adoption
            join NoteCollectionEntity source on source.id = adoption.sourcePlanId
            where adoption.sourcePlanId in :collectionIds
              and adoption.ownerUserId <> source.ownerUserId
            group by adoption.sourcePlanId
            """)
    List<NoteCollectionAdoptionCountProjection> countAdoptionsByCollectionIds(@Param("collectionIds") List<UUID> collectionIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select collection
            from NoteCollectionEntity collection
            where collection.ownerUserId = :ownerUserId
              and collection.sourcePlanId = :sourcePlanId
            """)
    Optional<NoteCollectionEntity> findByOwnerUserIdAndSourcePlanIdForUpdate(
            @Param("ownerUserId") UUID ownerUserId,
            @Param("sourcePlanId") UUID sourcePlanId
    );

    /**
     * Source-only status aggregation.  The source_plan_id predicate is intentional: adopted rows use
     * the same tables but their publication stamp is meaningless and must never affect this result.
     */
    @Query(value = """
            select
                exists (
                    select 1
                    from note_collections candidate
                    where candidate.source_plan_id is null
                      and (candidate.id = :collectionId or candidate.parent_collection_id = :collectionId)
                      and candidate.published_at is null
                    union all
                    select 1
                    from note_collection_items item
                    join note_collections candidate on candidate.id = item.collection_id
                    where candidate.source_plan_id is null
                      and (candidate.id = :collectionId or candidate.parent_collection_id = :collectionId)
                      and item.published_at is null
                ) as unpublished_changes,
                (
                    select count(*)
                    from note_collection_items item
                    join note_collections candidate on candidate.id = item.collection_id
                    where candidate.source_plan_id is null
                      and (candidate.id = :collectionId or candidate.parent_collection_id = :collectionId)
                      and item.published_at is null
                ) as topics_added,
                (
                    select count(*)
                    from note_collections candidate
                    where candidate.source_plan_id is null
                      and candidate.parent_collection_id = :collectionId
                      and candidate.published_at is null
                ) as subject_plans_added
            """, nativeQuery = true)
    ReviewSetPublicationStatusProjection getReviewSetPublicationStatus(@Param("collectionId") UUID collectionId);

    @Query(value = """
            update note_collections
            set published_at = :publishedAt
            where source_plan_id is null
              and published_at is null
              and (id = :collectionId or parent_collection_id = :collectionId)
            """, nativeQuery = true)
    @org.springframework.data.jpa.repository.Modifying
    int publishUnpublishedReviewSetCollections(
            @Param("collectionId") UUID collectionId,
            @Param("publishedAt") Instant publishedAt
    );

    @Query(value = """
            update note_collections
            set last_update_published_at = :publishedAt
            where id = :collectionId
            """, nativeQuery = true)
    @org.springframework.data.jpa.repository.Modifying
    int markReviewSetUpdatePublished(@Param("collectionId") UUID collectionId, @Param("publishedAt") Instant publishedAt);

    @Query("""
            select collection.lastUpdatePublishedAt
            from NoteCollectionEntity collection
            where collection.id = :collectionId
            """)
    Instant findLastUpdatePublishedAt(@Param("collectionId") UUID collectionId);
}
