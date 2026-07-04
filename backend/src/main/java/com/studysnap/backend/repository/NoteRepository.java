package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<NoteEntity, UUID> {
    String COLLECTION_NOTE_PROJECTION = """
             new com.studysnap.backend.repository.NoteCollectionNoteProjection(
                n.id,
                n.title,
                n.subject,
                n.courseProgram,
                n.status,
                n.visibility,
                n.updatedAt
            )
            """;

    Optional<NoteEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    Optional<NoteEntity> findByOwnerUserIdAndCopiedFromNoteIdAndCopiedFromPublicTrue(UUID ownerUserId, UUID copiedFromNoteId);
    List<NoteEntity> findByOwnerUserIdOrderByUpdatedAtDesc(UUID ownerUserId);
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
    List<NoteEntity> findByVisibilityAndTargetProfileTypeOrderByUpdatedAtDesc(NoteVisibility visibility, NoteTargetProfileType targetProfileType);

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
              and (:targetProfileType is null or n.targetProfileType = :targetProfileType)
              and (:creator is null or lower(u.username) = lower(:creator))
            order by n.updatedAt desc
            """)
    List<NoteEntity> findPublicNotes(
            @Param("visibility") NoteVisibility visibility,
            @Param("targetProfileType") NoteTargetProfileType targetProfileType,
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
            select n.copiedFromNoteId as noteId, count(n) as copyCount
            from NoteEntity n
            where n.copiedFromNoteId in :noteIds
              and n.copiedFromPublic = true
            group by n.copiedFromNoteId
            """)
    List<NoteCopyCountProjection> countCopiedPublicNotesBySourceNoteIds(@Param("noteIds") List<UUID> noteIds);
}
