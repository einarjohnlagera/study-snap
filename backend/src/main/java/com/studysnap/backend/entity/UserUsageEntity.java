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
@Table(name = "user_usage")
@Getter
@Setter
@NoArgsConstructor
public class UserUsageEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "period_start", nullable = false)
    private OffsetDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private OffsetDateTime periodEnd;

    @Column(name = "study_pack_generations", nullable = false)
    private Integer studyPackGenerations;

    @Column(name = "challenge_quiz_generations", nullable = false)
    private Integer challengeQuizGenerations;

    @Column(name = "adaptive_quiz_generations", nullable = false)
    private Integer adaptiveQuizGenerations;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
