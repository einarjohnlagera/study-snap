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
@Table(name = "quiz_share_links")
@Getter
@Setter
@NoArgsConstructor
public class QuizShareLinkEntity {
    @Id
    private UUID id;

    /** Exactly one of this and combinedQuizId is set; V132 enforces the exclusive arc in PostgreSQL. */
    @Column(name = "generated_quiz_id")
    private UUID generatedQuizId;

    @Column(name = "combined_quiz_id")
    private UUID combinedQuizId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(nullable = false, unique = true, length = 32)
    private String token;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
