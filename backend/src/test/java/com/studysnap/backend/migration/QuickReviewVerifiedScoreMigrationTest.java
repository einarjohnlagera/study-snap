package com.studysnap.backend.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the v0.74.0 backfill against the regression it exists to prevent: an existing learner
 * who already scored a perfect Quick Review must stay mastered, or the Quiz tab locks on deploy
 * day for someone who has been using it.
 */
class QuickReviewVerifiedScoreMigrationTest {
    private static final String MIGRATION_PATH = "db/migration/V111__quick_review_verified_score.sql";
    private static final String COLUMN_NAME = "verified_correct_answers";
    private static final UUID MASTERED_SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID IMPERFECT_SESSION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID IN_PROGRESS_SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CHALLENGE_SESSION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID NULL_SCORE_SESSION_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    void migrationGrandfathersCompletedQuickReviewScoresAndLeavesEverythingElseNull() throws Exception {
        String databaseName = "quick-review-verified-score-migration-" + UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        create table quick_review_sessions (
                            id uuid primary key,
                            session_mode varchar(32) not null,
                            status varchar(32) not null,
                            correct_answers integer,
                            total_questions integer not null
                        )
                        """);
                insertSession(statement, MASTERED_SESSION_ID, "QUICK_REVIEW", "COMPLETED", "5", 5);
                insertSession(statement, IMPERFECT_SESSION_ID, "QUICK_REVIEW", "COMPLETED", "4", 5);
                insertSession(statement, IN_PROGRESS_SESSION_ID, "QUICK_REVIEW", "IN_PROGRESS", "5", 5);
                insertSession(statement, CHALLENGE_SESSION_ID, "CHALLENGE", "COMPLETED", "5", 5);
                insertSession(statement, NULL_SCORE_SESSION_ID, "QUICK_REVIEW", "COMPLETED", null, 5);

                String migration = new ClassPathResource(MIGRATION_PATH).getContentAsString(StandardCharsets.UTF_8);
                statement.execute(migration);

                // The regression this test exists for: a pre-deploy perfect score stays perfect,
                // so findQuizMasteredAt still matches it against the current quiz size.
                assertThat(readVerifiedScore(statement, MASTERED_SESSION_ID)).isEqualTo(5);

                // A pre-deploy 4/5 is grandfathered honestly -- carried across, still short of the gate.
                assertThat(readVerifiedScore(statement, IMPERFECT_SESSION_ID)).isEqualTo(4);

                // Only completed Quick Review sessions are backfilled.
                assertThat(readVerifiedScore(statement, IN_PROGRESS_SESSION_ID)).isNull();
                assertThat(readVerifiedScore(statement, CHALLENGE_SESSION_ID)).isNull();

                // No recorded score is no evidence of mastery; null reads as not-mastered.
                assertThat(readVerifiedScore(statement, NULL_SCORE_SESSION_ID)).isNull();
            }
        }
    }

    private void insertSession(
            Statement statement,
            UUID id,
            String sessionMode,
            String status,
            String correctAnswers,
            int totalQuestions
    ) throws Exception {
        statement.execute(
                "insert into quick_review_sessions (id, session_mode, status, correct_answers, total_questions) values ('"
                        + id + "', '" + sessionMode + "', '" + status + "', "
                        + (correctAnswers == null ? "null" : correctAnswers) + ", " + totalQuestions + ")"
        );
    }

    private Integer readVerifiedScore(Statement statement, UUID sessionId) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(
                "select " + COLUMN_NAME + " from quick_review_sessions where id = '" + sessionId + "'"
        )) {
            assertThat(resultSet.next()).isTrue();
            int value = resultSet.getInt(COLUMN_NAME);
            return resultSet.wasNull() ? null : value;
        }
    }
}
