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

class ChallengeQuizQuestionBankNullLevelStampMigrationTest {
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String PACK_ID = "00000000-0000-0000-0000-000000000002";

    @Test
    void stampsUnclaimableNullLevelRowsAndLeavesDuplicatesOfClaimableRowsAlone() throws Exception {
        String databaseName = "challenge-bank-null-level-" + UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        create table challenge_quiz_question_bank (
                            id uuid primary key,
                            user_id uuid not null,
                            study_pack_id uuid not null,
                            question_key varchar(500) not null,
                            learner_level varchar(50)
                        )
                        """);

                // (a) unclaimable: NULL level, no COLLEGE twin -> must be stamped.
                insert(statement, "orphan-key", null);
                // (b) a duplicate of a row the learner can already receive -> must be left NULL,
                //     because stamping it would collide with the twin under V115's widened key.
                insert(statement, "duplicated-key", null);
                insert(statement, "duplicated-key", "COLLEGE");
                // (c) already claimable at a different level -> untouched, and does NOT count as a
                //     COLLEGE twin, so its NULL sibling is still stamped.
                insert(statement, "other-level-key", null);
                insert(statement, "other-level-key", "BOARD_EXAM_REVIEW");

                String migration = new ClassPathResource(
                        "db/migration/V116__challenge_quiz_question_bank_stamp_null_learner_level.sql"
                ).getContentAsString(StandardCharsets.UTF_8);
                statement.executeUpdate(migration);

                // Both previously-NULL rows are now claimable at COLLEGE.
                assertThat(nullCount(statement, "orphan-key")).isZero();
                assertThat(countAtLevel(statement, "orphan-key", "COLLEGE")).isEqualTo(1);
                assertThat(nullCount(statement, "other-level-key")).isZero();
                assertThat(countAtLevel(statement, "other-level-key", "COLLEGE")).isEqualTo(1);
                // The pre-existing row at another level is untouched.
                assertThat(countAtLevel(statement, "other-level-key", "BOARD_EXAM_REVIEW")).isEqualTo(1);
                // Nothing deleted, and the duplicate stays NULL rather than colliding.
                assertThat(countRows(statement)).isEqualTo(5);
                assertThat(nullCount(statement, "duplicated-key")).isEqualTo(1);
            }
        }
    }

    private void insert(Statement statement, String questionKey, String learnerLevel) throws Exception {
        String level = learnerLevel == null ? "null" : "'" + learnerLevel + "'";
        statement.executeUpdate(
                "insert into challenge_quiz_question_bank (id, user_id, study_pack_id, question_key, learner_level) values ('"
                        + UUID.randomUUID() + "', '" + USER_ID + "', '" + PACK_ID + "', '" + questionKey + "', " + level + ")"
        );
    }

    private int countAtLevel(Statement statement, String questionKey, String learnerLevel) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(
                "select count(*) from challenge_quiz_question_bank where question_key = '" + questionKey
                        + "' and learner_level = '" + learnerLevel + "'"
        )) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private int countRows(Statement statement) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("select count(*) from challenge_quiz_question_bank")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private int nullCount(Statement statement, String questionKey) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(
                "select count(*) from challenge_quiz_question_bank where question_key = '" + questionKey
                        + "' and learner_level is null"
        )) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }
}
