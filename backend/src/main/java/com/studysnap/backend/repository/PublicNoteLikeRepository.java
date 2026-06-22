package com.studysnap.backend.repository;

import com.studysnap.backend.entity.PublicNoteLikeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicNoteLikeRepository extends JpaRepository<PublicNoteLikeEntity, UUID> {
    Optional<PublicNoteLikeEntity> findByNoteIdAndUserId(UUID noteId, UUID userId);

    void deleteByUserId(UUID userId);

    @Query("""
            select l.noteId as noteId, count(l) as likeCount
            from PublicNoteLikeEntity l
            where l.noteId in :noteIds
            group by l.noteId
            """)
    List<PublicNoteLikeCountProjection> countLikesByNoteIds(@Param("noteIds") List<UUID> noteIds);

    @Query("""
            select l.noteId
            from PublicNoteLikeEntity l
            where l.userId = :userId
              and l.noteId in :noteIds
            """)
    List<UUID> findLikedNoteIdsByUserIdAndNoteIdIn(@Param("userId") UUID userId, @Param("noteIds") List<UUID> noteIds);
}
