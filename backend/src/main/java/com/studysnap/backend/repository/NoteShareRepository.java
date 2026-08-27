package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteShareEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteShareRepository extends JpaRepository<NoteShareEntity, UUID> {
    List<NoteShareEntity> findByNoteIdAndRevokedAtIsNullOrderByCreatedAtAsc(UUID noteId);

    Optional<NoteShareEntity> findFirstByNoteIdAndGranteeUserIdAndRevokedAtIsNull(
            UUID noteId,
            UUID granteeUserId
    );

    @Query(value = """
            select exists (
                select 1
                  from note_shares ns
                  join linked_learner_relationships relationship on relationship.id = ns.relationship_id
                  join users grantee on grantee.id = ns.grantee_user_id
                 where ns.note_id = :noteId
                   and ns.grantee_user_id = :granteeUserId
                   and ns.revoked_at is null
                   and relationship.status = 'ACCEPTED'
                   and grantee.email_verified_at is not null
                   and ((relationship.supporter_user_id = ns.owner_user_id
                         and relationship.learner_user_id = ns.grantee_user_id)
                     or (relationship.learner_user_id = ns.owner_user_id
                         and relationship.supporter_user_id = ns.grantee_user_id))
            )
            """, nativeQuery = true)
    boolean existsLiveAuthorizedShare(
            @Param("noteId") UUID noteId,
            @Param("granteeUserId") UUID granteeUserId
    );

    @Query(value = """
            select ns.*
              from note_shares ns
              join study_packs study_pack on study_pack.note_id = ns.note_id
             where study_pack.id = :studyPackId
               and ns.grantee_user_id = :granteeUserId
               and ns.revoked_at is null
             limit 1
            """, nativeQuery = true)
    Optional<NoteShareEntity> findLiveByStudyPackIdAndGranteeUserId(
            @Param("studyPackId") UUID studyPackId,
            @Param("granteeUserId") UUID granteeUserId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NoteShareEntity share
               set share.revokedAt = :revokedAt
             where share.noteId = :noteId
               and share.revokedAt is null
               and share.relationshipId in :relationshipIds
            """)
    int revokeLiveShares(
            @Param("noteId") UUID noteId,
            @Param("relationshipIds") List<UUID> relationshipIds,
            @Param("revokedAt") OffsetDateTime revokedAt
    );

    @Query(value = """
            select ns.id as shareId,
                   note.id as noteId,
                   note.title as title,
                   note.subject as subject,
                   owner.display_name as ownerDisplayName,
                   owner.first_name as ownerFirstName,
                   owner.last_name as ownerLastName,
                   owner.email as ownerEmail,
                   study_pack.id as studyPackId,
                   study_pack.status as studyPackStatus,
                   ns.created_at as sharedAt
              from note_shares ns
              join linked_learner_relationships relationship on relationship.id = ns.relationship_id
              join notes note on note.id = ns.note_id
              join users owner on owner.id = ns.owner_user_id
              left join study_packs study_pack on study_pack.note_id = note.id
             where ns.grantee_user_id = :granteeUserId
               and ns.revoked_at is null
               and relationship.status = 'ACCEPTED'
               and ((relationship.supporter_user_id = ns.owner_user_id
                     and relationship.learner_user_id = ns.grantee_user_id)
                 or (relationship.learner_user_id = ns.owner_user_id
                     and relationship.supporter_user_id = ns.grantee_user_id))
               and (:cursorCreatedAt is null
                    or ns.created_at < :cursorCreatedAt
                    or (ns.created_at = :cursorCreatedAt and ns.id < :cursorId))
             order by ns.created_at desc, ns.id desc
            """, nativeQuery = true)
    List<SharedNoteListProjection> findSharedWithMe(
            @Param("granteeUserId") UUID granteeUserId,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );
}
