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
}
