package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteCollectionItemRemovalEntity;
import com.studysnap.backend.entity.NoteCollectionItemRemovalId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NoteCollectionItemRemovalRepository
        extends JpaRepository<NoteCollectionItemRemovalEntity, NoteCollectionItemRemovalId> {
    List<NoteCollectionItemRemovalEntity> findByAdoptedCollectionIdIn(List<UUID> adoptedCollectionIds);
}
