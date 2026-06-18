package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "concept_health")
@Getter
@Setter
@NoArgsConstructor
public class ConceptHealthEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Column(nullable = false, length = 500)
    private String concept;

    @Column(name = "last_correct_at")
    private OffsetDateTime lastCorrectAt;

    @Column(name = "last_incorrect_at")
    private OffsetDateTime lastIncorrectAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
