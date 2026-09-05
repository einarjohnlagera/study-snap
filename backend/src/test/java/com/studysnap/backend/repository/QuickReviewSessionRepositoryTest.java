package com.studysnap.backend.repository;

import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.testutil.SqlCaptureStatementInspector;
import com.studysnap.backend.testutil.builders.QuickReviewSessionEntityBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.studysnap.backend.testutil.SqlCaptureStatementInspector")
@Transactional
class QuickReviewSessionRepositoryTest {

    @Autowired
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void initSchema() {
        // findQuizMasteredAt reads notes.generation_enqueued_at through a subquery, so the table has to
        // exist for the statement to run. Only the two columns the subquery touches are declared: this
        // class hand-rolls its schema, and mirroring more of production here would be drift waiting to
        // happen. The REAL-ROW guards for the regeneration clock live in NativeQueryPostgresIntegrationTest
        // against the actual Flyway schema, which is where a persisted-state assertion belongs.
        jdbcTemplate.execute("""
                create table if not exists notes (
                    id uuid primary key,
                    generation_enqueued_at timestamp with time zone
                )
                """);
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
    void findLatestCompletedSession_returnsMostRecentCompleted() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        saveSession(userId, studyPackId, QuickReviewSessionStatus.COMPLETED, now.minusHours(3), now.minusHours(2), 80);
        QuickReviewSessionEntity newest = saveSession(
                userId,
                studyPackId,
                QuickReviewSessionStatus.COMPLETED,
                now.minusHours(1),
                now.minusMinutes(10),
                60
        );

        List<QuickReviewSessionEntity> sessions = quickReviewSessionRepository
                .findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        PageRequest.of(0, 1)
                );

        assertThat(sessions).hasSize(1);
        assertThat(sessions.getFirst().getId()).isEqualTo(newest.getId());
    }

    @Test
    void findInProgressSession_returnsInProgressForUserAndStudyPack() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        QuickReviewSessionEntity inProgress = saveSession(
                userId,
                studyPackId,
                QuickReviewSessionStatus.IN_PROGRESS,
                now.minusMinutes(5),
                null,
                null
        );

        Optional<QuickReviewSessionEntity> found = quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        QuickReviewSessionStatus.IN_PROGRESS
                );

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().getId()).isEqualTo(inProgress.getId());
    }

    @Test
    void findInProgressSession_returnsEmptyWhenOnlyCompletedExists() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        saveSession(
                userId,
                studyPackId,
                QuickReviewSessionStatus.COMPLETED,
                now.minusMinutes(30),
                now.minusMinutes(10),
                75
        );

        Optional<QuickReviewSessionEntity> found = quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        QuickReviewSessionStatus.IN_PROGRESS
                );

        assertThat(found).isEmpty();
    }

    @Test
    void findCompletedSessions_ordersByCompletedAtDescending() {
        UUID userId = UUID.randomUUID();
        UUID studyPackIdA = UUID.randomUUID();
        UUID studyPackIdB = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        QuickReviewSessionEntity oldest = saveSession(
                userId,
                studyPackIdA,
                QuickReviewSessionStatus.COMPLETED,
                now.minusDays(1),
                now.minusHours(5),
                50
        );
        QuickReviewSessionEntity middle = saveSession(
                userId,
                studyPackIdB,
                QuickReviewSessionStatus.COMPLETED,
                now.minusHours(4),
                now.minusHours(2),
                70
        );
        QuickReviewSessionEntity newest = saveSession(
                userId,
                studyPackIdA,
                QuickReviewSessionStatus.COMPLETED,
                now.minusHours(1),
                now.minusMinutes(20),
                90
        );

        List<QuickReviewSessionEntity> sessions = quickReviewSessionRepository
                .findByUserIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        PageRequest.of(0, 10)
                );

        assertThat(sessions).extracting(QuickReviewSessionEntity::getId)
                .containsExactly(newest.getId(), middle.getId(), oldest.getId());
    }

    @Test
    void completedQueries_areScopedPerUser() {
        UUID targetUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        QuickReviewSessionEntity target = saveSession(
                targetUserId,
                studyPackId,
                QuickReviewSessionStatus.COMPLETED,
                now.minusMinutes(10),
                now.minusMinutes(5),
                65
        );
        saveSession(
                otherUserId,
                studyPackId,
                QuickReviewSessionStatus.COMPLETED,
                now.minusMinutes(9),
                now.minusMinutes(4),
                95
        );

        List<QuickReviewSessionEntity> sessions = quickReviewSessionRepository
                .findByUserIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        targetUserId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        PageRequest.of(0, 10)
                );

        assertThat(sessions).hasSize(1);
        assertThat(sessions.getFirst().getId()).isEqualTo(target.getId());
        assertThat(sessions.getFirst().getUserId()).isEqualTo(targetUserId);
    }

    @Test
    void completedQueries_areScopedPerStudyPack() {
        UUID userId = UUID.randomUUID();
        UUID targetStudyPackId = UUID.randomUUID();
        UUID otherStudyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        QuickReviewSessionEntity target = saveSession(
                userId,
                targetStudyPackId,
                QuickReviewSessionStatus.COMPLETED,
                now.minusMinutes(15),
                now.minusMinutes(5),
                45
        );
        saveSession(
                userId,
                otherStudyPackId,
                QuickReviewSessionStatus.COMPLETED,
                now.minusMinutes(14),
                now.minusMinutes(4),
                88
        );

        List<QuickReviewSessionEntity> sessions = quickReviewSessionRepository
                .findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        targetStudyPackId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        PageRequest.of(0, 10)
                );

        assertThat(sessions).hasSize(1);
        assertThat(sessions.getFirst().getId()).isEqualTo(target.getId());
        assertThat(sessions.getFirst().getStudyPackId()).isEqualTo(targetStudyPackId);
    }

    @Test
    void latestCompletedSession_isBasedOnRecencyNotBestScore() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        saveSession(
                userId,
                studyPackId,
                QuickReviewSessionStatus.COMPLETED,
                now.minusDays(1),
                now.minusHours(10),
                100
        );
        QuickReviewSessionEntity latestLowerScore = saveSession(
                userId,
                studyPackId,
                QuickReviewSessionStatus.COMPLETED,
                now.minusHours(2),
                now.minusMinutes(10),
                60
        );

        List<QuickReviewSessionEntity> sessions = quickReviewSessionRepository
                .findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        PageRequest.of(0, 1)
                );

        assertThat(sessions).hasSize(1);
        assertThat(sessions.getFirst().getId()).isEqualTo(latestLowerScore.getId());
        assertThat(sessions.getFirst().getScorePercentage()).isEqualByComparingTo("60.00");
    }

    @Test
    void findQuizMasteredAt_requiresCompletedPerfectQuickReviewForCurrentQuizSize() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        QuickReviewSessionEntity imperfectQuickReview = saveSession(
                userId,
                studyPackId,
                QuickReviewSessionStatus.COMPLETED,
                now.minusHours(4),
                now.minusHours(3),
                100
        );
        imperfectQuickReview.setVerifiedCorrectAnswers(4);
        quickReviewSessionRepository.save(imperfectQuickReview);

        QuickReviewSessionEntity challengeQuiz = saveSession(
                userId,
                studyPackId,
                QuickReviewSessionStatus.COMPLETED,
                now.minusHours(3),
                now.minusHours(2),
                100
        );
        challengeQuiz.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        challengeQuiz.setVerifiedCorrectAnswers(5);
        quickReviewSessionRepository.save(challengeQuiz);

        assertThat(quickReviewSessionRepository.findQuizMasteredAt(userId, studyPackId, 5, studyPackId)).isNull();

        QuickReviewSessionEntity masteredQuickReview = saveSession(
                userId,
                studyPackId,
                QuickReviewSessionStatus.COMPLETED,
                now.minusHours(2),
                now.minusHours(1),
                100
        );
        masteredQuickReview.setVerifiedCorrectAnswers(5);
        quickReviewSessionRepository.save(masteredQuickReview);

        assertThat(quickReviewSessionRepository.findQuizMasteredAt(userId, studyPackId, 5, studyPackId))
                .isEqualTo(masteredQuickReview.getCompletedAt());
        assertThat(quickReviewSessionRepository.findQuizMasteredAt(userId, studyPackId, 6, studyPackId)).isNull();
    }

    @Test
    void projectionQueries_doNotSelectSessionState() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        saveSession(
                userId,
                studyPackId,
                QuickReviewSessionStatus.COMPLETED,
                now.minusMinutes(15),
                now.minusMinutes(5),
                90
        );

        SqlCaptureStatementInspector.clear();

        List<QuickReviewSessionSummaryProjection> summaries = quickReviewSessionRepository
                .findCompletedSessionSummariesByUserIdAndSessionModeOrderByCompletedAtDesc(
                        userId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        PageRequest.of(0, 10)
                );
        List<QuickReviewSessionMetadataProjection> metadataRows = quickReviewSessionRepository
                .findCompletedSessionMetadataByUserIdAndSessionModeOrderByCompletedAtDesc(
                        userId,
                        QuickReviewSessionMode.QUICK_REVIEW
                );

        assertThat(summaries).hasSize(1);
        assertThat(metadataRows).hasSize(1);
        List<String> quickReviewSelects = SqlCaptureStatementInspector.statements().stream()
                .filter(sql -> sql.toLowerCase().contains("from quick_review_sessions"))
                .filter(sql -> sql.toLowerCase().startsWith("select"))
                .toList();
        assertThat(quickReviewSelects).isNotEmpty();
        assertThat(quickReviewSelects)
                .allSatisfy(sql -> assertThat(sql.toLowerCase()).doesNotContain("session_state"));
        assertThat(quickReviewSelects)
                .anySatisfy(sql -> assertThat(sql.toLowerCase()).contains("session_metadata"));
    }

    @Test
    void latestCompletionProjection_groupsByNoteAndDoesNotSelectJsonColumns() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID otherNoteId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        QuickReviewSessionEntity latest = saveSession(
                userId,
                UUID.randomUUID(),
                noteId,
                QuickReviewSessionStatus.COMPLETED,
                now.minusMinutes(15),
                now.minusMinutes(5),
                90
        );
        saveSession(
                userId,
                UUID.randomUUID(),
                noteId,
                QuickReviewSessionStatus.COMPLETED,
                now.minusMinutes(30),
                now.minusMinutes(20),
                80
        );
        saveSession(
                userId,
                UUID.randomUUID(),
                otherNoteId,
                QuickReviewSessionStatus.IN_PROGRESS,
                now.minusMinutes(10),
                null,
                null
        );
        saveSession(
                otherUserId,
                UUID.randomUUID(),
                noteId,
                QuickReviewSessionStatus.COMPLETED,
                now.minusMinutes(12),
                now.minusMinutes(1),
                100
        );

        SqlCaptureStatementInspector.clear();

        List<NoteLatestCompletionProjection> latestCompletions = quickReviewSessionRepository
                .findLatestCompletedAtByUserIdAndNoteIdIn(
                        userId,
                        QuickReviewSessionStatus.COMPLETED,
                        List.of(noteId, otherNoteId)
                );

        assertThat(latestCompletions).containsExactly(new NoteLatestCompletionProjection(noteId, latest.getCompletedAt()));
        List<String> quickReviewSelects = SqlCaptureStatementInspector.statements().stream()
                .filter(sql -> sql.toLowerCase().contains("from quick_review_sessions"))
                .filter(sql -> sql.toLowerCase().startsWith("select"))
                .toList();
        assertThat(quickReviewSelects).hasSize(1);
        assertThat(quickReviewSelects.getFirst().toLowerCase())
                .doesNotContain("session_state")
                .doesNotContain("session_metadata");
    }

    private QuickReviewSessionEntity saveSession(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt,
            Integer scorePercentage
    ) {
        return saveSession(userId, studyPackId, studyPackId, status, createdAt, completedAt, scorePercentage);
    }

    private QuickReviewSessionEntity saveSession(
            UUID userId,
            UUID studyPackId,
            UUID noteId,
            QuickReviewSessionStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt,
            Integer scorePercentage
    ) {
        QuickReviewRound round = status == QuickReviewSessionStatus.COMPLETED ? QuickReviewRound.RETRY : QuickReviewRound.INITIAL;
        Integer correctAnswers = scorePercentage == null ? null : Math.clamp(scorePercentage / 20, 0, 5);
        BigDecimal score = scorePercentage == null ? null : BigDecimal.valueOf(scorePercentage).setScale(2);

        QuickReviewSessionEntity session = QuickReviewSessionEntityBuilder.anInProgressSession()
                .withId(UUID.randomUUID())
                .withUserId(userId)
                .withStudyPackId(studyPackId)
                .withNoteId(noteId)
                .withStatus(status)
                .withCurrentQuestionIndex(status == QuickReviewSessionStatus.COMPLETED ? 5 : 0)
                .withCurrentRound(round)
                .withTotalQuestions(5)
                .withCorrectAnswers(correctAnswers)
                .withScorePercentage(score)
                .withRetryCount(status == QuickReviewSessionStatus.COMPLETED ? 1 : 0)
                .withDurationSeconds(120)
                .withSessionMetadata(null)
                .withSessionState(null)
                .withCreatedAt(createdAt)
                .withCompletedAt(completedAt)
                .build();
        return quickReviewSessionRepository.save(session);
    }
}
