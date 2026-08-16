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

class CuratorNoteLearnerLevelBackfillMigrationTest {
    private static final String MIGRATION_PATH =
            "db/migration/V117__backfill_curator_note_learner_level.sql";
    private static final String ADMIN = "ADMIN";
    private static final String USER = "USER";
    private static final String BOARD_TAKER = "BOARD_TAKER";
    private static final String PROFESSIONAL = "PROFESSIONAL";
    private static final String STUDENT = "STUDENT";
    private static final String BOARD_EXAM_REVIEW = "BOARD_EXAM_REVIEW";
    private static final String COLLEGE = "COLLEGE";
    private static final String INFORMATION_TECHNOLOGY = "Information Technology";

    @Test
    void mapsCuratorBoardAndProfessionalNotesWithoutPrograms() throws Exception {
        try (Connection connection = createDatabase(); Statement statement = connection.createStatement()) {
            UUID curatorId = insertUser(statement, ADMIN);
            UUID boardNoteId = insertNote(statement, curatorId, null, BOARD_TAKER, null);
            UUID professionalNoteId = insertNote(statement, curatorId, null, PROFESSIONAL, null);

            assertThat(executeMigration(statement)).isEqualTo(2);

            assertThat(readLearnerLevel(statement, boardNoteId)).isEqualTo(BOARD_EXAM_REVIEW);
            assertThat(readLearnerLevel(statement, professionalNoteId)).isEqualTo(PROFESSIONAL);
        }
    }

    @Test
    void leavesCuratorStudentDepthNull() throws Exception {
        try (Connection connection = createDatabase(); Statement statement = connection.createStatement()) {
            UUID curatorId = insertUser(statement, ADMIN);
            UUID noteId = insertNote(statement, curatorId, null, STUDENT, null);

            assertThat(executeMigration(statement)).isZero();

            assertThat(readLearnerLevel(statement, noteId)).isNull();
        }
    }

    @Test
    void leavesLearnerOwnedBoardDepthNull() throws Exception {
        try (Connection connection = createDatabase(); Statement statement = connection.createStatement()) {
            UUID learnerId = insertUser(statement, USER);
            UUID noteId = insertNote(statement, learnerId, null, BOARD_TAKER, null);

            assertThat(executeMigration(statement)).isZero();

            assertThat(readLearnerLevel(statement, noteId)).isNull();
        }
    }

    @Test
    void excludesBoardNoteWhenAnyCatalogProgramIsInformationTechnology() throws Exception {
        try (Connection connection = createDatabase(); Statement statement = connection.createStatement()) {
            UUID curatorId = insertUser(statement, ADMIN);
            UUID noteId = insertNote(statement, curatorId, null, BOARD_TAKER, null);
            UUID civilEngineeringId = insertCourseProgram(statement, "Civil Engineering");
            UUID informationTechnologyId = insertCourseProgram(statement, INFORMATION_TECHNOLOGY);
            linkProgram(statement, noteId, civilEngineeringId);
            linkProgram(statement, noteId, informationTechnologyId);

            assertThat(executeMigration(statement)).isZero();

            assertThat(readLearnerLevel(statement, noteId)).isNull();
        }
    }

    @Test
    void excludesBoardNoteWithFreeTextInformationTechnologyProgram() throws Exception {
        try (Connection connection = createDatabase(); Statement statement = connection.createStatement()) {
            UUID curatorId = insertUser(statement, ADMIN);
            UUID noteId = insertNote(statement, curatorId, INFORMATION_TECHNOLOGY, BOARD_TAKER, null);

            assertThat(executeMigration(statement)).isZero();

            assertThat(readLearnerLevel(statement, noteId)).isNull();
        }
    }

    @Test
    void preservesAlreadyAuthoredDepth() throws Exception {
        try (Connection connection = createDatabase(); Statement statement = connection.createStatement()) {
            UUID curatorId = insertUser(statement, ADMIN);
            UUID noteId = insertNote(statement, curatorId, null, BOARD_TAKER, COLLEGE);

            assertThat(executeMigration(statement)).isZero();

            assertThat(readLearnerLevel(statement, noteId)).isEqualTo(COLLEGE);
        }
    }

    @Test
    void rerunningMigrationIsANoOp() throws Exception {
        try (Connection connection = createDatabase(); Statement statement = connection.createStatement()) {
            UUID curatorId = insertUser(statement, ADMIN);
            UUID noteId = insertNote(statement, curatorId, null, BOARD_TAKER, null);

            assertThat(executeMigration(statement)).isEqualTo(1);
            assertThat(executeMigration(statement)).isZero();

            assertThat(readLearnerLevel(statement, noteId)).isEqualTo(BOARD_EXAM_REVIEW);
        }
    }

    private Connection createDatabase() throws Exception {
        String databaseName = "curator-note-level-backfill-" + UUID.randomUUID();
        Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL"
        );
        try (Statement statement = connection.createStatement()) {
            statement.execute("create table users (id uuid primary key, role varchar(50) not null)");
            statement.execute("""
                    create table notes (
                        id uuid primary key,
                        owner_user_id uuid not null,
                        course_program varchar(120),
                        target_profile_type varchar(50) not null,
                        learner_level varchar(50)
                    )
                    """);
            statement.execute("create table course_programs (id uuid primary key, name varchar(120) not null)");
            statement.execute("""
                    create table note_course_program (
                        id uuid primary key,
                        note_id uuid not null,
                        course_program_id uuid not null
                    )
                    """);
        }
        return connection;
    }

    private UUID insertUser(Statement statement, String role) throws Exception {
        UUID id = UUID.randomUUID();
        statement.executeUpdate("insert into users (id, role) values ('%s', '%s')".formatted(id, role));
        return id;
    }

    private UUID insertNote(
            Statement statement,
            UUID ownerUserId,
            String courseProgram,
            String targetProfileType,
            String learnerLevel
    ) throws Exception {
        UUID id = UUID.randomUUID();
        statement.executeUpdate("""
                insert into notes (id, owner_user_id, course_program, target_profile_type, learner_level)
                values ('%s', '%s', %s, '%s', %s)
                """.formatted(
                id,
                ownerUserId,
                sqlValue(courseProgram),
                targetProfileType,
                sqlValue(learnerLevel)
        ));
        return id;
    }

    private UUID insertCourseProgram(Statement statement, String name) throws Exception {
        UUID id = UUID.randomUUID();
        statement.executeUpdate("insert into course_programs (id, name) values ('%s', '%s')".formatted(id, name));
        return id;
    }

    private void linkProgram(Statement statement, UUID noteId, UUID courseProgramId) throws Exception {
        statement.executeUpdate("""
                insert into note_course_program (id, note_id, course_program_id)
                values ('%s', '%s', '%s')
                """.formatted(UUID.randomUUID(), noteId, courseProgramId));
    }

    private int executeMigration(Statement statement) throws Exception {
        String migration = new ClassPathResource(MIGRATION_PATH)
                .getContentAsString(StandardCharsets.UTF_8);
        return statement.executeUpdate(migration);
    }

    private String readLearnerLevel(Statement statement, UUID noteId) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(
                "select learner_level from notes where id = '" + noteId + "'"
        )) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private String sqlValue(String value) {
        return value == null ? "null" : "'" + value + "'";
    }
}
