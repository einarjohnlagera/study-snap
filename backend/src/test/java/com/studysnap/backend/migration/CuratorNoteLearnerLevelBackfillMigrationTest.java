package com.studysnap.backend.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    private static final String CIVIL_ENGINEERING = "Civil Engineering";

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
            UUID civilEngineeringId = insertCourseProgram(statement, CIVIL_ENGINEERING);
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

    // Academic-level values are levels wearing a program's clothing, so BOARD_TAKER beside one is a
    // mis-tag rather than a licensure claim -- the production audit found exactly that on a public
    // High School note whose curator authored JUNIOR_HIGH as its depth. Both program stores must
    // exclude them, and the Senior High strands must match on the prefix rather than the en dash.
    @ParameterizedTest
    @ValueSource(strings = {
            "Grade School",
            "Junior High",
            "Junior High School",
            "High School",
            "Senior High – STEM",
            "Senior High – HUMSS",
            "Senior High – ABM",
            "Senior High School"
    })
    void excludesBoardNoteWithFreeTextAcademicLevelProgram(String program) throws Exception {
        try (Connection connection = createDatabase(); Statement statement = connection.createStatement()) {
            UUID curatorId = insertUser(statement, ADMIN);
            UUID noteId = insertNote(statement, curatorId, program, BOARD_TAKER, null);

            assertThat(executeMigration(statement)).isZero();

            assertThat(readLearnerLevel(statement, noteId)).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Grade School", "Junior High", "High School", "Senior High – STEM"})
    void excludesBoardNoteWhenAnyCatalogProgramIsAnAcademicLevel(String program) throws Exception {
        try (Connection connection = createDatabase(); Statement statement = connection.createStatement()) {
            UUID curatorId = insertUser(statement, ADMIN);
            UUID noteId = insertNote(statement, curatorId, null, BOARD_TAKER, null);
            UUID civilEngineeringId = insertCourseProgram(statement, CIVIL_ENGINEERING);
            UUID academicLevelId = insertCourseProgram(statement, program);
            linkProgram(statement, noteId, civilEngineeringId);
            linkProgram(statement, noteId, academicLevelId);

            assertThat(executeMigration(statement)).isZero();

            assertThat(readLearnerLevel(statement, noteId)).isNull();
        }
    }

    // The denylist must not swallow legitimate licensure programs that merely share a word with it.
    @ParameterizedTest
    @ValueSource(strings = {CIVIL_ENGINEERING, "Nursing", "Accountancy", "Architecture", "Education"})
    void mapsBoardNotesForLicensureProgramsUnaffectedByTheDenylist(String program) throws Exception {
        try (Connection connection = createDatabase(); Statement statement = connection.createStatement()) {
            UUID curatorId = insertUser(statement, ADMIN);
            UUID noteId = insertNote(statement, curatorId, program, BOARD_TAKER, null);

            assertThat(executeMigration(statement)).isEqualTo(1);

            assertThat(readLearnerLevel(statement, noteId)).isEqualTo(BOARD_EXAM_REVIEW);
        }
    }

    // Every other ownership test has only one user in the database, so deleting the join correlation
    // `u.id = n.owner_user_id` leaves them all green while production stamps all 4,645 learner-owned
    // notes -- the exact outcome ADR-001 constraint 2 exists to prevent. This fixture is the only
    // thing that can see it: both users present, so an uncorrelated join reaches the learner's note.
    @Test
    void leavesLearnerOwnedNoteNullWhenACuratorExistsInTheSameDatabase() throws Exception {
        try (Connection connection = createDatabase(); Statement statement = connection.createStatement()) {
            UUID curatorId = insertUser(statement, ADMIN);
            UUID learnerId = insertUser(statement, USER);
            UUID curatorNoteId = insertNote(statement, curatorId, null, BOARD_TAKER, null);
            UUID learnerNoteId = insertNote(statement, learnerId, null, BOARD_TAKER, null);

            assertThat(executeMigration(statement)).isEqualTo(1);

            assertThat(readLearnerLevel(statement, curatorNoteId)).isEqualTo(BOARD_EXAM_REVIEW);
            assertThat(readLearnerLevel(statement, learnerNoteId)).isNull();
        }
    }

    // Same shape of gap on the other side: with only one note in the database, dropping
    // `ncp.note_id = n.id` from the NOT EXISTS turns a per-note check into a global one and every
    // test still passes, while production silently maps nothing. Two notes are required to see it.
    @Test
    void excludesOnlyTheNoteCarryingTheDenylistedProgram() throws Exception {
        try (Connection connection = createDatabase(); Statement statement = connection.createStatement()) {
            UUID curatorId = insertUser(statement, ADMIN);
            UUID excludedNoteId = insertNote(statement, curatorId, null, BOARD_TAKER, null);
            UUID eligibleNoteId = insertNote(statement, curatorId, null, BOARD_TAKER, null);
            UUID informationTechnologyId = insertCourseProgram(statement, INFORMATION_TECHNOLOGY);
            UUID civilEngineeringId = insertCourseProgram(statement, CIVIL_ENGINEERING);
            linkProgram(statement, excludedNoteId, informationTechnologyId);
            linkProgram(statement, eligibleNoteId, civilEngineeringId);

            assertThat(executeMigration(statement)).isEqualTo(1);

            assertThat(readLearnerLevel(statement, excludedNoteId)).isNull();
            assertThat(readLearnerLevel(statement, eligibleNoteId)).isEqualTo(BOARD_EXAM_REVIEW);
        }
    }

    // The denylist gates the PROFESSIONAL mapping too. Zero rows in production today, which is
    // precisely why it needs pinning -- nothing else would notice if the guard were dropped.
    @Test
    void excludesProfessionalNoteWithADenylistedProgram() throws Exception {
        try (Connection connection = createDatabase(); Statement statement = connection.createStatement()) {
            UUID curatorId = insertUser(statement, ADMIN);
            UUID noteId = insertNote(statement, curatorId, "Grade School", PROFESSIONAL, null);

            assertThat(executeMigration(statement)).isZero();

            assertThat(readLearnerLevel(statement, noteId)).isNull();
        }
    }

    // trim() is load-bearing for values arriving with stray whitespace; no other case exercises it.
    @Test
    void excludesBoardNoteWhenTheDenylistedProgramHasSurroundingWhitespace() throws Exception {
        try (Connection connection = createDatabase(); Statement statement = connection.createStatement()) {
            UUID curatorId = insertUser(statement, ADMIN);
            UUID noteId = insertNote(statement, curatorId, "  Information Technology  ", BOARD_TAKER, null);

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
