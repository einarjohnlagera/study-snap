package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteCollectionItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteCollectionItemRepository extends JpaRepository<NoteCollectionItemEntity, UUID> {
    List<NoteCollectionItemEntity> findByCollectionIdOrderByPositionAsc(UUID collectionId);

    Optional<NoteCollectionItemEntity> findByCollectionIdAndNoteId(UUID collectionId, UUID noteId);

    @Query("""
            select i.collectionId as collectionId, count(i.id) as itemCount
            from NoteCollectionItemEntity i
            where i.collectionId in :collectionIds
            group by i.collectionId
            """)
    List<NoteCollectionItemCountProjection> countItemsByCollectionIds(@Param("collectionIds") List<UUID> collectionIds);
}
