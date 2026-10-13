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

    List<NoteCollectionItemEntity> findByCollectionIdInOrderByCollectionIdAscPositionAsc(List<UUID> collectionIds);

    long countByCollectionId(UUID collectionId);

    Optional<NoteCollectionItemEntity> findByCollectionIdAndNoteId(UUID collectionId, UUID noteId);

    void deleteByCollectionIdIn(List<UUID> collectionIds);

    @Query("""
            select i.collectionId as collectionId, count(i.id) as itemCount
            from NoteCollectionItemEntity i
            where i.collectionId in :collectionIds
            group by i.collectionId
            """)
    List<NoteCollectionItemCountProjection> countItemsByCollectionIds(@Param("collectionIds") List<UUID> collectionIds);

    @Query("""
            select i.collectionId as collectionId, i.noteId as noteId
            from NoteCollectionItemEntity i
            where i.collectionId in :collectionIds
            order by i.collectionId asc, i.position asc
            """)
    List<NoteCollectionItemNoteProjection> findNoteIdsByCollectionIds(@Param("collectionIds") List<UUID> collectionIds);

    @Query("""
            select collection.id
            from NoteCollectionItemEntity item
            join NoteCollectionEntity collection on collection.id = item.collectionId
            where item.noteId = :noteId
              and collection.ownerUserId = :ownerUserId
            order by collection.updatedAt desc, collection.id asc
            """)
    List<UUID> findContainingCollectionIdsByNoteIdAndOwnerUserIdOrderByUpdatedAtDesc(
            @Param("noteId") UUID noteId,
            @Param("ownerUserId") UUID ownerUserId
    );

    /** One deterministic Official root, newest first; root items do not require a publish stamp. */
    @Query(value = """
            select root.id as id, root.title as title
            from note_collection_items item
            join note_collections placement on placement.id = item.collection_id
            join note_collections root on root.id = coalesce(placement.parent_collection_id, placement.id)
            join users owner on owner.id = root.owner_user_id
            where item.note_id = :noteId
              and root.parent_collection_id is null
              and root.source_plan_id is null
              and root.visibility = 'PUBLIC'
              and owner.role = 'ADMIN'
              and owner.id <> :deletedUserId
              and (placement.parent_collection_id is null
                   or (placement.published_at is not null and item.published_at is not null))
            order by root.updated_at desc, root.id asc
            limit 1
            """, nativeQuery = true)
    Optional<OfficialReviewSetProjection> findOfficialReviewSetRootForNote(
            @Param("noteId") UUID noteId,
            @Param("deletedUserId") UUID deletedUserId
    );

    /**
     * Readable items of one collection in plan order, excluding the note just completed.
     *
     * <p>Deliberately does NOT filter on practice state. "Practiced" has exactly one definition —
     * {@code QuizSessionHistoryService.findLatestSessionCompletedAtByNoteIds}, the same source behind
     * {@code NoteCollectionService.toProgressResponse}'s {@code lastSessionCompletedAt != null} — and it
     * counts multi-note sessions (Board/Long Exam) that no {@code session.noteId} predicate can see. A
     * {@code not exists} filter here would be a second, narrower definition that silently disagrees.
     */
    @Query("""
            select item.noteId
            from NoteCollectionItemEntity item
            join NoteEntity note on note.id = item.noteId
            where item.collectionId = :collectionId
              and item.noteId <> :excludedNoteId
              and (note.ownerUserId = :userId
                   or note.visibility = com.studysnap.backend.entity.NoteVisibility.PUBLIC)
            order by item.position asc, item.id asc
            """)
    List<UUID> findReadableNoteIdsByCollectionIdOrderByPositionAsc(
            @Param("collectionId") UUID collectionId,
            @Param("userId") UUID userId,
            @Param("excludedNoteId") UUID excludedNoteId
    );

    /** Publishes only source-side rows beneath a locked Official root. */
    @Query(value = """
            update note_collection_items item
            set published_at = :publishedAt
            from note_collections collection
            where collection.id = item.collection_id
              and collection.source_plan_id is null
              and item.published_at is null
              and (collection.id = :collectionId or collection.parent_collection_id = :collectionId)
            """, nativeQuery = true)
    @org.springframework.data.jpa.repository.Modifying
    int publishUnpublishedReviewSetItems(
            @Param("collectionId") UUID collectionId,
            @Param("publishedAt") java.time.Instant publishedAt
    );
}
