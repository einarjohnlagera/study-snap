package com.studysnap.backend.entity;

import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.dto.CompanionStructureSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "learner_level", length = 50)
    private LearnerLevel learnerLevel;

    @Column(name = "estimated_study_hours")
    private Integer estimatedStudyHours;

    @Column(name = "target_completion_date")
    private LocalDate targetCompletionDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "companion", columnDefinition = "jsonb")
    private CompanionContent companion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "companion_structure_snapshot", columnDefinition = "jsonb")
    private CompanionStructureSnapshot companionStructureSnapshot;

    @Column(name = "source_plan_id")
    private UUID sourcePlanId;

    @Column(name = "source_title_at_sync", length = 150)
    private String sourceTitleAtSync;

    @Column(name = "source_parent_id_at_sync")
    private UUID sourceParentIdAtSync;

    @Column(name = "source_position_at_sync")
    private Integer sourcePositionAtSync;

    @Column(name = "source_synced_at")
    private Instant sourceSyncedAt;

    @Column(name = "parent_collection_id")
    private UUID parentCollectionId;

    @Column(name = "sibling_position")
    private Integer siblingPosition;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
