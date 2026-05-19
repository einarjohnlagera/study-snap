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

    @Column(name = "interview_practice_used_this_month", nullable = false)
    private Integer interviewPracticeUsedThisMonth;

    @Column(name = "long_exam_used_this_month", nullable = false)
    private Integer longExamUsedThisMonth;

    @Column(name = "board_exam_used_this_month", nullable = false)
    private Integer boardExamUsedThisMonth;

    @Column(name = "ocr_extractions", nullable = false)
    private Integer ocrExtractions;

    @Column(name = "note_generations", nullable = false)
    private Integer noteGenerations;

    @Column(name = "exports_count", nullable = false)
    private Integer exportsCount;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public void incrementInterviewPracticeUsedThisMonth() {
        interviewPracticeUsedThisMonth = interviewPracticeUsedThisMonth == null
                ? 1
                : interviewPracticeUsedThisMonth + 1;
    }

    public void incrementLongExamUsedThisMonth() {
        longExamUsedThisMonth = longExamUsedThisMonth == null
                ? 1
                : longExamUsedThisMonth + 1;
    }

    public void incrementBoardExamUsedThisMonth() {
        boardExamUsedThisMonth = boardExamUsedThisMonth == null
                ? 1
                : boardExamUsedThisMonth + 1;
    }
}
