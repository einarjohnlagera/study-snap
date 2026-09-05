package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "note_collection_item_removals")
@IdClass(NoteCollectionItemRemovalId.class)
@Getter
@Setter
@NoArgsConstructor
public class NoteCollectionItemRemovalEntity {
    @Id
    @Column(name = "adopted_collection_id", nullable = false)
    private UUID adoptedCollectionId;

    @Id
    @Column(name = "source_plan_id", nullable = false)
    private UUID sourcePlanId;

    @Id
    @Column(name = "source_note_id", nullable = false)
    private UUID sourceNoteId;

    @Column(name = "removed_at", nullable = false)
    private Instant removedAt;
}
