package com.studysnap.backend.repository;

import com.studysnap.backend.entity.GeneratedQuizEntity;
import com.studysnap.backend.entity.LearnerLevel;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeneratedQuizRepository extends JpaRepository<GeneratedQuizEntity, UUID> {
    String NOTE_GENERATED_QUIZ_PROJECTION = """
             new com.studysnap.backend.repository.GeneratedQuizNoteProjection(
                q.noteId,
                q.id
            )
            """;

    Optional<GeneratedQuizEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    Optional<GeneratedQuizEntity> findByNoteId(UUID noteId);
    Optional<GeneratedQuizEntity> findByNoteIdAndOwnerUserId(UUID noteId, UUID ownerUserId);
    List<GeneratedQuizEntity> findByOwnerUserIdAndNoteIdIn(UUID ownerUserId, List<UUID> noteIds);

    @Query("""
            select """ + NOTE_GENERATED_QUIZ_PROJECTION + """
            from GeneratedQuizEntity q
            where q.ownerUserId = :ownerUserId
              and q.noteId in :noteIds
            """)
    List<GeneratedQuizNoteProjection> findNoteIdsByOwnerUserIdAndNoteIdIn(
            @Param("ownerUserId") UUID ownerUserId,
            @Param("noteIds") Collection<UUID> noteIds
    );

    void deleteByOwnerUserId(UUID ownerUserId);
    List<GeneratedQuizEntity> findByNoteIdAndTargetLearnerLevelIsNotNullOrderByGeneratedAtDesc(
            UUID noteId,
            Pageable pageable
    );

    default Optional<LearnerLevel> findLatestTargetLearnerLevelByNoteId(UUID noteId) {
        return findByNoteIdAndTargetLearnerLevelIsNotNullOrderByGeneratedAtDesc(
                noteId,
                PageRequest.of(0, 1)
        ).stream()
                .map(GeneratedQuizEntity::getTargetLearnerLevel)
                .findFirst();
    }
}
