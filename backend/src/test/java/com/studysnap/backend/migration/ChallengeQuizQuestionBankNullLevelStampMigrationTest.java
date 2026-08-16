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
    private static final String NOTE_ID = "00000000-0000-0000-0000-000000000003";

    @Test
    void stampsUnclaimableNullLevelRowsAndLeavesDuplicatesOfClaimableRowsAlone() throws Exception {
        String databaseName = "challenge-bank-null-level-" + UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        create table notes (
                            id uuid primary key,
                            learner_level varchar(50)
                        )
                        """);
                statement.executeUpdate("""
                        create table study_packs (
                            id uuid primary key,
                            note_id uuid not null
                        )
                        """);
                statement.executeUpdate("""
                        create table challenge_quiz_question_bank (
                            id uuid primary key,
                            user_id uuid not null,
                            study_pack_id uuid not null,
                            question_key varchar(500) not null,
                            learner_level varchar(50)
                        )
                        """);

                // The pack's note carries JUNIOR_HIGH, so a blanket COLLEGE stamp would leave these
                // rows just as unclaimable. The migration must resolve the note's level.
                statement.executeUpdate("insert into notes (id, learner_level) values ('"
                        + NOTE_ID + "', 'JUNIOR_HIGH')");
                statement.executeUpdate("insert into study_packs (id, note_id) values ('"
                        + PACK_ID + "', '" + NOTE_ID + "')");

                // (a) unclaimable: NULL level -> must be stamped with the note's level.
                insert(statement, "orphan-key", null);
                // (b) DEFENSIVE ONLY -- unreachable in production. The pre-V115 key made
                //     (user, pack, question_key) unique across all three NOT NULL columns, so a NULL
                //     row cannot have a levelled twin. Kept to pin the guard's behaviour if the
                //     migration is ever re-run against post-V115 data. This H2 table declares no
                //     unique constraint, so it cannot demonstrate V115's key -- do not read it as doing so.
                //     The twin sits at JUNIOR_HIGH, the level this row WOULD resolve to, which is the
                //     only case the guard has to catch.
                insert(statement, "duplicated-key", null);
                insert(statement, "duplicated-key", "JUNIOR_HIGH");
                // (c) already claimable at a different level -> untouched, and does NOT count as a
                //     COLLEGE twin, so its NULL sibling is still stamped.
                insert(statement, "other-level-key", null);
                insert(statement, "other-level-key", "BOARD_EXAM_REVIEW");

                String migration = new ClassPathResource(
                        "db/migration/V116__challenge_quiz_question_bank_stamp_null_learner_level.sql"
                ).getContentAsString(StandardCharsets.UTF_8);
                statement.executeUpdate(migration);

                // Both previously-NULL rows now carry the NOTE's level, not a blanket COLLEGE.
                assertThat(nullCount(statement, "orphan-key")).isZero();
                assertThat(countAtLevel(statement, "orphan-key", "JUNIOR_HIGH")).isEqualTo(1);
                assertThat(nullCount(statement, "other-level-key")).isZero();
                assertThat(countAtLevel(statement, "other-level-key", "JUNIOR_HIGH")).isEqualTo(1);
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
