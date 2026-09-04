package com.studysnap.backend.entity;

import com.studysnap.backend.exception.QuickReviewSessionAnchorException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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

    @Column(name = "study_pack_id", nullable = true)
    private UUID studyPackId;

    @Column(name = "note_id", nullable = true)
    private UUID noteId;

    @Column(name = "source_collection_id", nullable = true)
    private UUID sourceCollectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_mode", nullable = false, length = 32)
    private QuickReviewSessionMode sessionMode;

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

    @Column(name = "verified_correct_answers")
    private Integer verifiedCorrectAnswers;

    @Column(name = "score_percentage", precision = 5, scale = 2)
    private BigDecimal scorePercentage;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", length = 16)
    private QuickReviewConfidenceLevel confidenceLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "session_metadata", columnDefinition = "jsonb")
    private Map<String, Object> sessionMetadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "session_state", columnDefinition = "jsonb")
    private Map<String, Object> sessionState;

    @Column(name = "quota_exempt", nullable = false)
    private boolean quotaExempt;

    @Column(name = "model_used", length = 64)
    private String modelUsed;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "cached_input_tokens")
    private Integer cachedInputTokens;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    @PreUpdate
    void validateAnchor() {
        // A TERMINAL session is history and may legitimately be anchorless: deleting a Study Plan sets
        // source_collection_id to NULL rather than destroying the learner's completed sessions, and
        // session_state.sourceNoteRefs is what keeps them reachable. Mirrors
        // chk_quick_review_sessions_anchor, which carries the same terminal branch -- without this the
        // first save of an orphaned row would throw where the database would not.
        if (status == QuickReviewSessionStatus.COMPLETED || status == QuickReviewSessionStatus.FORFEITED) {
            return;
        }
        boolean hasPackAndNote = studyPackId != null && noteId != null;
        boolean hasPartialPackAndNote = (studyPackId == null) != (noteId == null);
        boolean hasCollection = sourceCollectionId != null;
        if (hasPartialPackAndNote || hasPackAndNote == hasCollection) {
            throw new QuickReviewSessionAnchorException();
        }
    }
}
