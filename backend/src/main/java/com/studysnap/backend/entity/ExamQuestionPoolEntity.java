package com.studysnap.backend.entity;

import com.studysnap.backend.dto.QuizItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "exam_question_pool")
@Getter
@Setter
@NoArgsConstructor
public class ExamQuestionPoolEntity {
    @Id
    private UUID id;

    @Column(name = "study_pack_id", nullable = false)
    private UUID studyPackId;

    @Column(nullable = false, length = 20)
    private String mode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<QuizItem> questions;

    @Column(name = "pool_size", nullable = false)
    private Integer poolSize;

    @Column(name = "generation_status", nullable = false, length = 20)
    private String generationStatus;

    @Column(name = "generation_status_at")
    private OffsetDateTime generationStatusAt;

    @Column(name = "learner_level", length = 50)
    private String learnerLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "served_question_keys", nullable = false, columnDefinition = "jsonb")
    private List<String> servedQuestionKeys;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "refreshed_at")
    private OffsetDateTime refreshedAt;
}
