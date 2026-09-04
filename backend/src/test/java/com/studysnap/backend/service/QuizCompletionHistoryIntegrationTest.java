package com.studysnap.backend.service;

import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class QuizCompletionHistoryIntegrationTest {
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-07-20T08:00:00Z");

    @Autowired
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("""
                create table if not exists quick_review_sessions (
                    id uuid primary key,
                    user_id uuid not null,
                    study_pack_id uuid,
                    note_id uuid,
                    source_collection_id uuid,
                    session_mode varchar(32) not null,
                    status varchar(32) not null,
                    current_question_index integer not null,
                    current_round varchar(16) not null,
                    total_questions integer not null,
                    correct_answers integer,
                    verified_correct_answers integer,
                    score_percentage numeric(5,2),
                    retry_count integer,
                    duration_seconds integer,
                    confidence_level varchar(16),
                    session_metadata json,
                    session_state json,
                    quota_exempt boolean not null default false,
                    model_used varchar(64),
                    input_tokens integer,
                    output_tokens integer,
                    cached_input_tokens integer,
                    created_at timestamp with time zone not null,
                    completed_at timestamp with time zone
                )
                """);
        jdbcTemplate.execute("delete from quick_review_sessions");
        // ⚠️ v0.113.1. Mirrors V133's chk_quick_review_sessions_anchor so a test cannot persist an
        // anchor shape the real database rejects. H2 in PostgreSQL mode DOES support CHECK.
        // ⚠️ It does NOT support PARTIAL unique indexes (a WHERE clause on CREATE INDEX) -- verified
        // empirically, not assumed -- so V133's three active-session indexes are NOT expressible
        // here. NativeQueryPostgresIntegrationTest is their only authority; do not assume this
        // fixture proves anything about them.
        jdbcTemplate.execute("alter table quick_review_sessions add constraint if not exists"
                + " chk_quick_review_sessions_anchor check ((study_pack_id is not null and note_id is not null)"
                + " or source_collection_id is not null or status in ('COMPLETED', 'FORFEITED'))");
        jdbcTemplate.execute("alter table quick_review_sessions add column if not exists quota_exempt boolean not null default false");
    }

    @Test
    void completedQuizExistenceSpansModesAndStudyPacks() {
        UUID userId = UUID.randomUUID();

        assertThat(hasCompletedQuiz(userId)).isFalse();

        saveCompletedSession(userId, UUID.randomUUID(), QuickReviewSessionMode.ADAPTIVE, BASE_TIME);
        entityManager.flush();
        assertThat(hasCompletedQuiz(userId)).isTrue();

        saveCompletedSession(userId, UUID.randomUUID(), QuickReviewSessionMode.LONG_EXAM, BASE_TIME.plusHours(1));
        entityManager.flush();
        assertThat(hasCompletedQuiz(userId)).isTrue();
        assertThat(hasCompletedQuiz(UUID.randomUUID())).isFalse();
    }

    private boolean hasCompletedQuiz(UUID userId) {
        return quickReviewSessionRepository.existsByUserIdAndStatusAndCompletedAtIsNotNull(
                userId,
                QuickReviewSessionStatus.COMPLETED
        );
    }

    private void saveCompletedSession(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionMode mode,
            OffsetDateTime completedAt
    ) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(UUID.randomUUID());
        session.setSessionMode(mode);
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(1);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(1);
        session.setCorrectAnswers(1);
        session.setScorePercentage(new BigDecimal("100.00"));
        session.setRetryCount(0);
        session.setCreatedAt(completedAt.minusMinutes(1));
        session.setCompletedAt(completedAt);
        quickReviewSessionRepository.save(session);
    }
}
