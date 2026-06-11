package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "note_collection_items")
@Getter
@Setter
@NoArgsConstructor
public class NoteCollectionItemEntity {

    @Id
    private UUID id;

    @Column(name = "collection_id", nullable = false)
    private UUID collectionId;

    @Column(name = "note_id", nullable = false)
    private UUID noteId;

    @Column(length = 120)
    private String label;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
