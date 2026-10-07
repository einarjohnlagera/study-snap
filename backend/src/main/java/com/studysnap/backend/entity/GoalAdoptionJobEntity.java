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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "goal_adoption_jobs")
@Getter
@Setter
@NoArgsConstructor
public class GoalAdoptionJobEntity {
    @Id
    private UUID id;
    @Column(name = "goal_id", nullable = false)
    private UUID goalId;
    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;
    @Column(name = "source_goal_id", nullable = false)
    private UUID sourceGoalId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_child_ids", nullable = false, columnDefinition = "jsonb")
    private List<UUID> sourceChildIds;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalAdoptionJobStatus status;
    @Column(name = "run_token")
    private UUID runToken;
    @Column(name = "processed_subject_count", nullable = false)
    private int processedSubjectCount;
    @Column(name = "adopted_subject_count", nullable = false)
    private int adoptedSubjectCount;
    @Column(name = "skipped_subject_count", nullable = false)
    private int skippedSubjectCount;
    @Column(name = "total_notes_copied", nullable = false)
    private int totalNotesCopied;
    @Column(name = "total_notes_skipped", nullable = false)
    private int totalNotesSkipped;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
