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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "quick_review_sessions")
@Getter
@Setter
@NoArgsConstructor
public class QuickReviewSessionEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private QuickReviewSessionStatus status;

    @Column(name = "current_question_index", nullable = false)
    private Integer currentQuestionIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_round", nullable = false, length = 16)
    private QuickReviewRound currentRound;

    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions;

    @Column(name = "correct_answers")
    private Integer correctAnswers;

    @Column(name = "score_percentage", precision = 5, scale = 2)
    private BigDecimal scorePercentage;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "session_metadata", columnDefinition = "jsonb")
    private Map<String, Object> sessionMetadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "session_state", columnDefinition = "jsonb")
    private Map<String, Object> sessionState;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
