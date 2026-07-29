package com.studysnap.backend.repository;

import com.studysnap.backend.entity.CollectionVisibility;
import com.studysnap.backend.entity.NoteCollectionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    long countByParentCollectionId(UUID parentCollectionId);

    long countByOwnerUserIdAndParentCollectionIdIsNull(UUID ownerUserId);

    @Query("""
            select collection.parentCollectionId as collectionId, count(collection.id) as childCount
            from NoteCollectionEntity collection
            where collection.parentCollectionId in :collectionIds
            group by collection.parentCollectionId
            """)
    List<NoteCollectionChildCountProjection> countChildrenByCollectionIds(@Param("collectionIds") List<UUID> collectionIds);

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
}
