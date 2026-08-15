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
}
