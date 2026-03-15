package com.studysnap.backend.repository;

import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
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

@SpringBootTest
@Transactional
class QuickReviewSessionRepositoryTest {

    @Autowired
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("""
                create table if not exists quick_review_sessions (
                    id uuid primary key,
                    user_id uuid not null,
                    study_pack_id uuid not null,
                    status varchar(32) not null,
                    current_question_index integer not null,
                    current_round varchar(16) not null,
                    total_questions integer not null,
                    correct_answers integer,
                    score_percentage numeric(5,2),
                    retry_count integer,
                    duration_seconds integer,
                    confidence_level varchar(16),
                    session_metadata json,
                    session_state json,
                    created_at timestamp with time zone not null,
                    completed_at timestamp with time zone
                )
                """);
        jdbcTemplate.execute("delete from quick_review_sessions");
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
                .findByUserIdAndStudyPackIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
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
                .findTopByUserIdAndStudyPackIdAndStatusOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
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
                .findTopByUserIdAndStudyPackIdAndStatusOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
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
                .findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
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
                .findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        targetUserId,
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
                .findByUserIdAndStudyPackIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        targetStudyPackId,
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
                .findByUserIdAndStudyPackIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        PageRequest.of(0, 1)
                );

        assertThat(sessions).hasSize(1);
        assertThat(sessions.getFirst().getId()).isEqualTo(latestLowerScore.getId());
        assertThat(sessions.getFirst().getScorePercentage()).isEqualByComparingTo("60.00");
    }

    private QuickReviewSessionEntity saveSession(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt,
            Integer scorePercentage
    ) {
        QuickReviewRound round = status == QuickReviewSessionStatus.COMPLETED ? QuickReviewRound.RETRY : QuickReviewRound.INITIAL;
        Integer correctAnswers = scorePercentage == null ? null : Math.max(0, Math.min(5, scorePercentage / 20));
        BigDecimal score = scorePercentage == null ? null : BigDecimal.valueOf(scorePercentage).setScale(2);

        QuickReviewSessionEntity session = QuickReviewSessionEntityBuilder.anInProgressSession()
                .withId(UUID.randomUUID())
                .withUserId(userId)
                .withStudyPackId(studyPackId)
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
