package com.studysnap.backend.entity;

import com.studysnap.backend.model.NoteListItemView;
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
import java.util.UUID;

@Entity
@Table(name = "notes")
@Getter
@Setter
@NoArgsConstructor
public class NoteEntity implements NoteListItemView {

    @Id
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column
    private String title;

    @Column(length = 64)
    private String subject;

    @Column(name = "course_program", length = 120)
    private String courseProgram;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain_context", length = 64)
    private DomainContext domainContext;

    @Enumerated(EnumType.STRING)
    @Column(name = "learner_level", length = 32)
    private LearnerLevel learnerLevel;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    private String[] tags;

    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NoteStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NoteVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_profile_type", nullable = false, length = 16)
    private NoteTargetProfileType targetProfileType;

    @Column(name = "source_note_id")
    private UUID sourceNoteId;

    @Column(name = "copied_from_note_id")
    private UUID copiedFromNoteId;

    @Column(name = "copied_from_user_id")
    private UUID copiedFromUserId;

    @Column(name = "copied_from_title")
    private String copiedFromTitle;

    @Column(name = "copied_from_public")
    private Boolean copiedFromPublic;

    @Column(name = "copied_at")
    private OffsetDateTime copiedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "generation_enqueued_at")
    private OffsetDateTime generationEnqueuedAt;

    /**
     * WHY the last generation attempt on this note failed -- the code half of {@code v0.87.0}'s
     * {@code (code, reason)} shape, normalized by {@link
     * com.studysnap.backend.service.BulkGenerationFailureReasonNormalizer}.
     *
     * <p>⚠️ THIS IS A LAST-FAILURE RECORD, NOT A DESCRIPTION OF THE CURRENT STATUS, AND IT IS
     * DELIBERATELY NOT CLEARED WHEN A LATER ATTEMPT SUCCEEDS. Regeneration mutates the note in place,
     * so the owner's manual retry overwrote {@code status} and destroyed the only evidence of the
     * 2026-09-05 incident; a retry must not be able to erase it a second time. Read it together with
     * {@link #generationFailedAt}, which is what distinguishes a current failure from a recovered one.
     */
    @Column(name = "generation_failure_code")
    private String generationFailureCode;

    /**
     * The safe, learner-readable half of the same pair. ⚠️ NEVER RAW EXCEPTION TEXT for a
     * non-{@code AppException} failure -- see the normalizer.
     */
    @Column(name = "generation_failure_reason")
    private String generationFailureReason;

    /** When the failure above was recorded. Written with the reason, and never cleared with it. */
    @Column(name = "generation_failed_at")
    private OffsetDateTime generationFailedAt;
}
