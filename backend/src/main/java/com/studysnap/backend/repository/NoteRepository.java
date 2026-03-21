package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<NoteEntity, UUID> {
    Optional<NoteEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    List<NoteEntity> findByOwnerUserIdOrderByUpdatedAtDesc(UUID ownerUserId);
    Optional<NoteEntity> findByIdAndVisibility(UUID id, NoteVisibility visibility);

    @Query("""
            select n
            from NoteEntity n
            where n.visibility = :visibility
              and (:excludeOwnerUserId is null or n.ownerUserId <> :excludeOwnerUserId)
            order by n.updatedAt desc
            """)
    List<NoteEntity> findByVisibilityExcludingOwnerOrderByUpdatedAtDesc(
            @Param("visibility") NoteVisibility visibility,
            @Param("excludeOwnerUserId") UUID excludeOwnerUserId
    );
}
