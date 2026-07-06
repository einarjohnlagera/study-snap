package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "note_collections")
@Getter
@Setter
@NoArgsConstructor
public class NoteCollectionEntity {

    @Id
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CollectionVisibility visibility = CollectionVisibility.PRIVATE;

    @Column(name = "course_program", length = 120)
    private String courseProgram;

    @Column(name = "estimated_study_hours")
    private Integer estimatedStudyHours;

    @Column(name = "target_completion_date")
    private LocalDate targetCompletionDate;

    @Column(name = "source_plan_id")
    private UUID sourcePlanId;

    @Column(name = "parent_collection_id")
    private UUID parentCollectionId;

    @Column(name = "sibling_position")
    private Integer siblingPosition;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
