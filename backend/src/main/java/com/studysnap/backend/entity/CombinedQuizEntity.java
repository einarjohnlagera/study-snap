package com.studysnap.backend.entity;

import com.studysnap.backend.dto.CombinedQuizSection;
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

/**
 * An immutable, note-independent snapshot. Its sections deliberately contain copied note titles and
 * copied questions, so a source note can be regenerated or deleted without changing a live shared quiz.
 */
@Entity
@Table(name = "combined_quizzes")
@Getter
@Setter
@NoArgsConstructor
public class CombinedQuizEntity {
    @Id
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(nullable = false, length = 512)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<CombinedQuizSection> sections;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
