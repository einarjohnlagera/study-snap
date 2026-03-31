package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteVisibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<NoteEntity, UUID> {
    Optional<NoteEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    List<NoteEntity> findByOwnerUserIdOrderByUpdatedAtDesc(UUID ownerUserId);
    Optional<NoteEntity> findByIdAndVisibility(UUID id, NoteVisibility visibility);
    List<NoteEntity> findByVisibilityAndSubjectIgnoreCaseOrderByUpdatedAtDesc(NoteVisibility visibility, String subject);
    List<NoteEntity> findByVisibilityAndSubjectIsNullOrderByUpdatedAtDesc(NoteVisibility visibility);
    long countByVisibility(NoteVisibility visibility);

    List<NoteEntity> findByVisibilityOrderByUpdatedAtDesc(NoteVisibility visibility);
}
