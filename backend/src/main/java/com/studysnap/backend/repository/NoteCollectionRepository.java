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

    Optional<NoteCollectionEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    Optional<NoteCollectionEntity> findByIdAndVisibility(UUID id, CollectionVisibility visibility);

    List<NoteCollectionEntity> findByVisibilityOrderByUpdatedAtDesc(CollectionVisibility visibility);

    List<NoteCollectionEntity> findByVisibilityAndCourseProgramOrderByUpdatedAtDesc(
            CollectionVisibility visibility,
            String courseProgram
    );

    Optional<NoteCollectionEntity> findByOwnerUserIdAndSourcePlanId(UUID ownerUserId, UUID sourcePlanId);

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
