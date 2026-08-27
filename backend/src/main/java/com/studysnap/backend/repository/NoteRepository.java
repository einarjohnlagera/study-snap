package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.model.NoteListItemProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<NoteEntity, UUID>, NoteLibraryRepository, PublicLibraryRepository {
    String COLLECTION_NOTE_PROJECTION = """
             new com.studysnap.backend.repository.NoteCollectionNoteProjection(
                n.id,
                n.title,
                n.subject,
                n.courseProgram,
                n.domainContext,
                n.learnerLevel,
                n.status,
                n.visibility,
                n.updatedAt
            )
            """;

    Optional<NoteEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NoteEntity n where n.id = :id")
    Optional<NoteEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select n.id
            from NoteEntity n
            where n.status = :status
              and n.generationEnqueuedAt < :cutoff
            order by n.generationEnqueuedAt asc
            """)
    List<UUID> findStaleGenerationIds(
            @Param("status") NoteStatus status,
            @Param("cutoff") OffsetDateTime cutoff,
            Pageable pageable
    );

    long countByStatusAndGenerationEnqueuedAtIsNull(NoteStatus status);
    Optional<NoteEntity> findByOwnerUserIdAndCopiedFromNoteIdAndCopiedFromPublicTrue(UUID ownerUserId, UUID copiedFromNoteId);

    /**
     * Copy-dedup lookup that ignores {@code copiedFromPublic}.
     *
     * <p>⚠️ The {@code ...CopiedFromPublicTrue} variant above cannot dedup a copy taken from a SHARED note:
     * a shared note is `PRIVATE`, so the flag is written {@code false} and the guard never matches, leaving
     * "Copy to my Library" able to mint unlimited duplicates on repeat presses. Only non-owner copies ever
     * populate {@code copied_from_note_id}, so widening the lookup changes nothing for the public path.
     */
    Optional<NoteEntity> findFirstByOwnerUserIdAndCopiedFromNoteId(UUID ownerUserId, UUID copiedFromNoteId);
    Page<NoteEntity> findByOwnerUserId(UUID ownerUserId, Pageable pageable);
    List<NoteEntity> findByOwnerUserIdOrderByUpdatedAtDesc(UUID ownerUserId);
    long countByOwnerUserId(UUID ownerUserId);

    @Query("""
            select new com.studysnap.backend.repository.NoteStatusProjection(
                n.id,
                n.status,
                n.updatedAt
            )
            from NoteEntity n
            where n.ownerUserId = :ownerUserId
            order by n.updatedAt desc
            """)
    List<NoteStatusProjection> findStatusProjectionsByOwnerUserIdOrderByUpdatedAtDesc(
            @Param("ownerUserId") UUID ownerUserId
    );

    List<NoteEntity> findByOwnerUserIdAndIdIn(UUID ownerUserId, List<UUID> ids);
    List<NoteEntity> findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(UUID ownerUserId, NoteVisibility visibility);
    Optional<NoteEntity> findByIdAndVisibility(UUID id, NoteVisibility visibility);
    List<NoteEntity> findByVisibilityAndSubjectIsNullOrderByUpdatedAtDesc(NoteVisibility visibility);
    long countByVisibility(NoteVisibility visibility);
    List<NoteEntity> findByOwnerUserIdAndVisibility(UUID ownerUserId, NoteVisibility visibility);
    void deleteByOwnerUserIdAndVisibility(UUID ownerUserId, NoteVisibility visibility);

    @Query("""
            select """ + COLLECTION_NOTE_PROJECTION + """
            from NoteEntity n
            where n.id in :noteIds
            """)
    List<NoteCollectionNoteProjection> findCollectionNoteProjectionsByIdIn(@Param("noteIds") Collection<UUID> noteIds);

    @Modifying
    @Query("""
            update NoteEntity n
            set n.ownerUserId = :nextOwnerUserId,
                n.updatedAt = :updatedAt
            where n.ownerUserId = :currentOwnerUserId
              and n.visibility = :visibility
            """)
    int reassignOwnerByOwnerUserIdAndVisibility(
            @Param("currentOwnerUserId") UUID currentOwnerUserId,
            @Param("nextOwnerUserId") UUID nextOwnerUserId,
            @Param("visibility") NoteVisibility visibility,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    List<NoteEntity> findByVisibilityOrderByUpdatedAtDesc(NoteVisibility visibility);

    @Query("""
            select count(distinct n.ownerUserId)
            from NoteEntity n
            where n.createdAt < :cutoff
              and not exists (
                  select 1
                  from StudyPackEntity sp
                  where sp.ownerUserId = n.ownerUserId
              )
              and exists (
                  select 1
                  from UserEntity u
                  where u.id = n.ownerUserId
                    and u.emailVerifiedAt is not null
              )
            """)
    long countVerifiedUsersWithNotesBeforeAndNoStudyPacks(@Param("cutoff") OffsetDateTime cutoff);

    @Query("""
            select n
            from NoteEntity n
            join UserEntity u on u.id = n.ownerUserId
            where n.visibility = :visibility
              and (:creator is null or lower(u.username) = lower(:creator))
            order by n.updatedAt desc
            """)
    List<NoteEntity> findPublicNotes(
            @Param("visibility") NoteVisibility visibility,
            @Param("creator") String creator
    );

    @Query("""
            select n.subject
            from NoteEntity n
            where n.subject is not null
              and trim(n.subject) <> ''
            """)
    List<String> findAllSubjectValues();

    @Query("""
            select n.courseProgram
            from NoteEntity n
            where n.ownerUserId = :ownerUserId
              and n.courseProgram is not null
              and trim(n.courseProgram) <> ''
            """)
    List<String> findCourseProgramValuesByOwnerUserId(@Param("ownerUserId") UUID ownerUserId);

    @Query("""
            select distinct n.courseProgram
            from NoteEntity n
            where n.ownerUserId = :ownerUserId
              and n.courseProgram is not null
              and trim(n.courseProgram) <> ''
            order by n.courseProgram
            """)
    List<String> findDistinctCourseProgramsByOwnerUserId(@Param("ownerUserId") UUID ownerUserId);

    @Query("""
            select n.courseProgram
            from NoteEntity n
            where n.visibility = :visibility
              and n.courseProgram is not null
              and trim(n.courseProgram) <> ''
            """)
    List<String> findCourseProgramValuesByVisibility(@Param("visibility") NoteVisibility visibility);

    @Query("""
            select n.subject
            from NoteEntity n
            where n.ownerUserId = :ownerUserId
              and n.subject is not null
              and trim(n.subject) <> ''
            """)
    List<String> findSubjectValuesByOwnerUserId(@Param("ownerUserId") UUID ownerUserId);

    @Query("""
            select n.subject as subject, count(n) as noteCount
            from NoteEntity n
            where n.ownerUserId = :ownerUserId
              and n.visibility = :visibility
              and n.subject is not null
              and trim(n.subject) <> ''
            group by n.subject
            order by count(n) desc
            """)
    List<NoteSubjectCountProjection> countSubjectsByOwnerUserIdAndVisibility(
            @Param("ownerUserId") UUID ownerUserId,
            @Param("visibility") NoteVisibility visibility
    );

    @Query("""
            select n.subject
            from NoteEntity n
            where n.visibility = :visibility
              and n.subject is not null
              and trim(n.subject) <> ''
            """)
    List<String> findSubjectValuesByVisibility(@Param("visibility") NoteVisibility visibility);

    @Query("""
            select distinct n.learnerLevel
            from NoteEntity n
            where n.visibility = :visibility
              and n.learnerLevel is not null
            order by n.learnerLevel
            """)
    List<LearnerLevel> findLearnerLevelsByVisibility(@Param("visibility") NoteVisibility visibility);

    @Query("""
            select n.copiedFromNoteId as noteId, count(n) as copyCount
            from NoteEntity n
            where n.copiedFromNoteId in :noteIds
              and n.copiedFromPublic = true
            group by n.copiedFromNoteId
            """)
    List<NoteCopyCountProjection> countCopiedPublicNotesBySourceNoteIds(@Param("noteIds") List<UUID> noteIds);

    @Query("""
            select source.id as noteId, count(distinct copy.ownerUserId) as learnerCount
            from NoteEntity source
            join NoteEntity copy on copy.copiedFromNoteId = source.id
            join QuickReviewSessionEntity session on session.noteId = copy.id
            where source.id in :noteIds
              and source.visibility = com.studysnap.backend.entity.NoteVisibility.PUBLIC
              and copy.copiedFromPublic = true
              and session.status = com.studysnap.backend.entity.QuickReviewSessionStatus.COMPLETED
              and session.completedAt is not null
            group by source.id
            """)
    List<NoteLearnersHelpedProjection> countDistinctLearnersHelpedBySourceNoteIds(
            @Param("noteIds") List<UUID> noteIds
    );

    @Query("""
            select count(distinct copy.ownerUserId)
            from NoteEntity source
            join NoteEntity copy on copy.copiedFromNoteId = source.id
            join QuickReviewSessionEntity session on session.noteId = copy.id
            where source.ownerUserId = :creatorUserId
              and source.visibility = com.studysnap.backend.entity.NoteVisibility.PUBLIC
              and copy.copiedFromPublic = true
              and session.status = com.studysnap.backend.entity.QuickReviewSessionStatus.COMPLETED
              and session.completedAt is not null
            """)
    long countDistinctLearnersHelpedByCreatorUserId(@Param("creatorUserId") UUID creatorUserId);

    @Query("""
            select count(distinct copy.ownerUserId)
            from NoteEntity source
            join NoteEntity copy on copy.copiedFromNoteId = source.id
            join QuickReviewSessionEntity session on session.noteId = copy.id
            where source.ownerUserId = :creatorUserId
              and source.visibility = com.studysnap.backend.entity.NoteVisibility.PUBLIC
              and copy.copiedFromPublic = true
              and session.status = com.studysnap.backend.entity.QuickReviewSessionStatus.COMPLETED
              and session.completedAt >= :since
            """)
    long countDistinctLearnersHelpedByCreatorUserIdSince(
            @Param("creatorUserId") UUID creatorUserId,
            @Param("since") OffsetDateTime since
    );
}
