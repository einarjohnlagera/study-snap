package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<NoteEntity, UUID> {
    Optional<NoteEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
