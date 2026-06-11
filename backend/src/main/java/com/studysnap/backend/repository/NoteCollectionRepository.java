package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteCollectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteCollectionRepository extends JpaRepository<NoteCollectionEntity, UUID> {
    List<NoteCollectionEntity> findByOwnerUserIdOrderByUpdatedAtDesc(UUID ownerUserId);

    Optional<NoteCollectionEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
