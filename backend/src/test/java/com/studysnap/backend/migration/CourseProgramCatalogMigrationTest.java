package com.studysnap.backend.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CourseProgramCatalogMigrationTest {
    private static final String NOTES_TABLE = "notes";
    private static final String USERS_TABLE = "users";
    private static final String COURSE_PROGRAMS_TABLE = "course_programs";
    private static final String PROGRAM_FAMILIES_TABLE = "program_families";
    private static final String EDUCATION = "Education";
    private static final String SENIOR_HIGH_ABM = "Senior High – ABM";
    private static final String EDUCATION_NOTE_ID = "00000000-0000-0000-0000-000000000001";
    private static final String CIVIL_SERVICE_NOTE_ID = "00000000-0000-0000-0000-000000000002";
    private static final String BIOLOGY_NOTE_ID = "00000000-0000-0000-0000-000000000003";
    private static final String GRADE_SCHOOL_NOTE_ID = "00000000-0000-0000-0000-000000000004";
    private static final String ABM_NOTE_ID = "00000000-0000-0000-0000-000000000005";
    private static final String ABM_USER_ID = "00000000-0000-0000-0000-000000000011";
    private static final String BSED_USER_ID = "00000000-0000-0000-0000-000000000012";
    private static final String COMPUTER_SCIENCE_USER_ID = "00000000-0000-0000-0000-000000000013";
    private static final List<String> EXCLUDED_VALUES = List.of(
            "Junior High",
            "High School",
            "Grade School",
            "Civil Service",
            "Professional / Board Exam Review",
            "Self Study / Personal Learning",
            "Engineering",
            "Biology",
            "Software Engineering",
            "Computer Science",
            "Bsed"
    );
    private static final List<String> EN_DASH_VALUES = List.of(
            SENIOR_HIGH_ABM,
            "Senior High – STEM",
            "Senior High – HUMSS",
            "Special Needs Education – Generalist"
    );

    @Test
    void migrationSeedsCatalogAndBackfillsOnlySanctionedMatches() throws Exception {
        String databaseName = "course-program-catalog-migration-" + UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("create table notes (id uuid primary key, course_program varchar(120))");
                statement.execute("create table users (id uuid primary key, course_program varchar(120))");
                seedFixtures(statement);
                Map<UUID, String> noteStringsBefore = readCoursePrograms(statement, NOTES_TABLE);
                Map<UUID, String> userStringsBefore = readCoursePrograms(statement, USERS_TABLE);

                String migration = new ClassPathResource(
                        "db/migration/V106__course_program_catalog.sql"
                ).getContentAsString(StandardCharsets.UTF_8);
                // Guard the deploy-log evidence block against a silent future deletion: it is the only
                // thing that surfaces a zero-match backfill, which would otherwise report success.
                assertThat(migration).contains("RAISE NOTICE");
                statement.execute(stripInformationalPlPgSqlBlock(migration));

                assertThat(count(statement, PROGRAM_FAMILIES_TABLE)).isEqualTo(1);
                assertThat(count(statement, COURSE_PROGRAMS_TABLE)).isEqualTo(21);
                assertThat(readNames(statement, PROGRAM_FAMILIES_TABLE)).containsExactly("Engineering");
                assertThat(readNames(statement, COURSE_PROGRAMS_TABLE)).contains("Information Technology");
                assertThat(readNames(statement, COURSE_PROGRAMS_TABLE)).doesNotContainAnyElementsOf(EXCLUDED_VALUES);
                assertExamGoalMappings(statement);
                assertEnDashValues(statement);
                assertExactAndExcludedBackfills(statement);
                assertBsedAlias(statement);
                assertThat(readCoursePrograms(statement, NOTES_TABLE)).isEqualTo(noteStringsBefore);
                assertThat(readCoursePrograms(statement, USERS_TABLE)).isEqualTo(userStringsBefore);
            }
        }
    }

    /**
     * H2 has no PL/pgSQL, so the migration's trailing {@code DO $$ ... $$} block cannot be parsed here.
     * It only emits {@code RAISE NOTICE} backfill counts for the deploy log and asserts nothing, so
     * dropping it costs this test no coverage — every count it reports is asserted directly against the
     * migrated tables below.
     */
    private String stripInformationalPlPgSqlBlock(String migration) {
        int blockStart = migration.indexOf("DO $$");
        return blockStart < 0 ? migration : migration.substring(0, blockStart);
    }

    private void seedFixtures(Statement statement) throws Exception {
        statement.execute("""
                insert into notes (id, course_program) values
                    ('00000000-0000-0000-0000-000000000001', 'Education'),
                    ('00000000-0000-0000-0000-000000000002', 'Civil Service'),
                    ('00000000-0000-0000-0000-000000000003', 'Biology'),
                    ('00000000-0000-0000-0000-000000000004', 'Grade School'),
                    ('00000000-0000-0000-0000-000000000005', 'Senior High – ABM'),
                    ('00000000-0000-0000-0000-000000000006', null)
                """);
        statement.execute("""
                insert into users (id, course_program) values
                    ('00000000-0000-0000-0000-000000000011', 'Senior High – ABM'),
                    ('00000000-0000-0000-0000-000000000012', 'Bsed'),
                    ('00000000-0000-0000-0000-000000000013', 'Computer Science'),
                    ('00000000-0000-0000-0000-000000000014', null)
                """);
    }

    private void assertExactAndExcludedBackfills(Statement statement) throws Exception {
        assertThat(readFkName(statement, NOTES_TABLE, EDUCATION_NOTE_ID)).isEqualTo(EDUCATION);
        assertThat(readFkName(statement, NOTES_TABLE, ABM_NOTE_ID)).isEqualTo(SENIOR_HIGH_ABM);
        assertThat(readFkName(statement, USERS_TABLE, ABM_USER_ID)).isEqualTo(SENIOR_HIGH_ABM);
        assertThat(readFkName(statement, NOTES_TABLE, CIVIL_SERVICE_NOTE_ID)).isNull();
        assertThat(readFkName(statement, NOTES_TABLE, BIOLOGY_NOTE_ID)).isNull();
        assertThat(readFkName(statement, NOTES_TABLE, GRADE_SCHOOL_NOTE_ID)).isNull();
        assertThat(readFkName(statement, USERS_TABLE, COMPUTER_SCIENCE_USER_ID)).isNull();
    }

    private void assertBsedAlias(Statement statement) throws Exception {
        assertThat(readFkName(statement, USERS_TABLE, BSED_USER_ID)).isEqualTo(EDUCATION);
        try (ResultSet resultSet = statement.executeQuery("""
                select count(*)
                from users u
                join course_programs cp on cp.id = u.course_program_id
                where u.course_program <> cp.name
                  and not (u.course_program = 'Bsed' and cp.name = 'Education')
                """)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isZero();
        }
    }

    private void assertEnDashValues(Statement statement) throws Exception {
        List<String> names = readNames(statement, COURSE_PROGRAMS_TABLE);
        assertThat(names).containsAll(EN_DASH_VALUES);
        EN_DASH_VALUES.forEach(value -> {
            assertThat(value).contains("\u2013");
            assertThat(value).doesNotContain("-");
        });
    }

    private void assertExamGoalMappings(Statement statement) throws Exception {
        assertThat(readProgramNamesForExamGoal(statement, "ale")).containsExactly("Architecture");
        assertThat(readProgramNamesForExamGoal(statement, "pnle")).containsExactly("Nursing");
        assertThat(readProgramNamesForExamGoal(statement, "let")).containsExactly("Education");
        assertThat(readProgramNamesForExamGoal(statement, "cpale")).containsExactly("Accountancy");
    }

    private List<String> readProgramNamesForExamGoal(Statement statement, String examGoalSlug) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                select name
                from course_programs
                where exam_goal_slug = '%s'
                order by name
                """.formatted(examGoalSlug))) {
            java.util.ArrayList<String> names = new java.util.ArrayList<>();
            while (resultSet.next()) {
                names.add(resultSet.getString(1));
            }
            return names;
        }
    }

    private Map<UUID, String> readCoursePrograms(Statement statement, String table) throws Exception {
        Map<UUID, String> values = new LinkedHashMap<>();
        try (ResultSet resultSet = statement.executeQuery("select id, course_program from " + table + " order by id")) {
            while (resultSet.next()) {
                values.put(resultSet.getObject("id", UUID.class), resultSet.getString("course_program"));
            }
        }
        return values;
    }

    private String readFkName(Statement statement, String table, String id) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                select cp.name
                from %s source
                left join course_programs cp on cp.id = source.course_program_id
                where source.id = '%s'
                """.formatted(table, id))) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private List<String> readNames(Statement statement, String table) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("select name from " + table + " order by name")) {
            java.util.ArrayList<String> names = new java.util.ArrayList<>();
            while (resultSet.next()) {
                names.add(resultSet.getString(1));
            }
            return names;
        }
    }

    private int count(Statement statement, String table) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("select count(*) from " + table)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }
}
