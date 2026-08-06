package com.studysnap.backend.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@code V108__remove_derived_learner_note_programs.sql}.
 *
 * <p>The doctrine under test (ADR-001 -> "Representation authority"): a learner's personal free-text
 * Course / Program must not be mechanically materialized into a catalog Applicable Program row. That is
 * deliberately narrower than "learner-owned notes cannot carry join rows" -- curator-authored rows and rows
 * inherited by copying a curated note are legitimate and must survive.
 */
class DerivedLearnerNoteProgramRemovalMigrationTest {
    private static final String MIGRATION_PATH = "db/migration/V108__remove_derived_learner_note_programs.sql";
    private static final String JOIN_TABLE = "note_course_program";

    private static final String LEARNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ADMIN_USER_ID = "00000000-0000-0000-0000-000000000002";
    private static final String TEACHER_USER_ID = "00000000-0000-0000-0000-000000000003";
    private static final String MISSING_USER_ID = "00000000-0000-0000-0000-0000000000ff";

    private static final String LEARNER_NON_COPY_NOTE_ID = "00000000-0000-0000-0000-000000000101";
    private static final String LEARNER_MULTI_ROW_NOTE_ID = "00000000-0000-0000-0000-000000000102";
    private static final String LEARNER_NO_ROW_NOTE_ID = "00000000-0000-0000-0000-000000000103";
    private static final String ADMIN_NOTE_ID = "00000000-0000-0000-0000-000000000104";
    private static final String TEACHER_NOTE_ID = "00000000-0000-0000-0000-000000000105";
    private static final String LEARNER_COPIED_FROM_NOTE_ID = "00000000-0000-0000-0000-000000000106";
    private static final String LEARNER_SOURCE_NOTE_ID = "00000000-0000-0000-0000-000000000107";
    private static final String ORPHAN_OWNER_NOTE_ID = "00000000-0000-0000-0000-000000000108";

    private static final List<String> SURVIVING_NOTE_IDS = List.of(
            ADMIN_NOTE_ID,
            TEACHER_NOTE_ID,
            LEARNER_COPIED_FROM_NOTE_ID,
            LEARNER_SOURCE_NOTE_ID,
            ORPHAN_OWNER_NOTE_ID
    );
    private static final List<String> REMOVED_NOTE_IDS = List.of(
            LEARNER_NON_COPY_NOTE_ID,
            LEARNER_MULTI_ROW_NOTE_ID
    );

    @Test
    void migrationRemovesOnlyMechanicallyDerivedLearnerRows() throws Exception {
        String databaseName = "derived-learner-note-program-removal-" + UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL")) {
            try (Statement statement = connection.createStatement()) {
                createSchema(statement);
                seedFixtures(statement);

                // Seven fixture notes carry rows, one of them two rows: eight rows before the delete.
                assertThat(count(statement, JOIN_TABLE)).isEqualTo(8);

                String migration = strippedMigration();
                statement.execute(migration);

                // The rows V107 mechanically derived from a learner's personal string are gone...
                REMOVED_NOTE_IDS.forEach(noteId -> assertThat(joinRowCountFor(statement, noteId))
                        .as("derived rows on learner-owned non-copy note %s", noteId)
                        .isZero());
                // ...and everything with legitimate provenance survives. Asserting the survivors, not only
                // the deletions, is what makes an over-broad DELETE fail instead of passing.
                SURVIVING_NOTE_IDS.forEach(noteId -> assertThat(joinRowCountFor(statement, noteId))
                        .as("legitimately provenanced row on note %s must survive", noteId)
                        .isEqualTo(1));
                assertThat(count(statement, JOIN_TABLE)).isEqualTo(5);
                assertThat(noteIdsWithJoinRows(statement)).containsExactlyInAnyOrderElementsOf(SURVIVING_NOTE_IDS);

                // A learner-owned non-copy note that never had a row is untouched and raises nothing.
                assertThat(joinRowCountFor(statement, LEARNER_NO_ROW_NOTE_ID)).isZero();
                assertThat(count(statement, "notes")).isEqualTo(8);

                // Re-running is a no-op.
                statement.execute(migration);
                assertThat(count(statement, JOIN_TABLE)).isEqualTo(5);
                assertThat(noteIdsWithJoinRows(statement)).containsExactlyInAnyOrderElementsOf(SURVIVING_NOTE_IDS);
            }
        }
    }

    /**
     * Structural guard against the trap documented in pressure-test finding B4: {@code V107}'s backfill sits
     * inside its PL/pgSQL block, which this strip helper removes, so nothing tests it. If someone later moves
     * {@code V108}'s DELETE into its reporting block, this test would keep passing while asserting nothing.
     * Assert the statement is still present after stripping so that move fails loudly instead.
     */
    @Test
    void deleteSurvivesThePlPgSqlStrippingThatWouldOtherwiseHideIt() throws Exception {
        assertThat(strippedMigration().toLowerCase()).contains("delete from " + JOIN_TABLE);
    }

    private String strippedMigration() throws Exception {
        String migration = new ClassPathResource(MIGRATION_PATH).getContentAsString(StandardCharsets.UTF_8);
        return stripInformationalPlPgSqlBlock(migration);
    }

    /**
     * H2 has no PL/pgSQL, so the migration's trailing reporting block cannot be parsed here. It only emits
     * {@code RAISE NOTICE} residual counts for the deploy log and asserts nothing. Mirrors
     * {@code CourseProgramCatalogMigrationTest}'s helper deliberately: the DELETE under test is kept above
     * the block precisely so that this stripping costs no coverage.
     */
    private String stripInformationalPlPgSqlBlock(String migration) {
        int blockStart = migration.indexOf("DO $$");
        return blockStart < 0 ? migration : migration.substring(0, blockStart);
    }

    private void createSchema(Statement statement) throws Exception {
        statement.execute("""
                create table users (
                    id uuid primary key,
                    role varchar(32) not null,
                    profile_type varchar(32)
                )
                """);
        statement.execute("""
                create table notes (
                    id uuid primary key,
                    owner_user_id uuid not null,
                    copied_from_note_id uuid,
                    source_note_id uuid
                )
                """);
        statement.execute("""
                create table note_course_program (
                    id uuid primary key,
                    note_id uuid not null,
                    course_program_id uuid not null
                )
                """);
    }

    private void seedFixtures(Statement statement) throws Exception {
        // The two owner fixtures are deliberately discriminating on ONE attribute each, so that dropping
        // either half of condition 1 fails a test: the admin is not a teacher, the teacher is not an admin.
        statement.execute("""
                insert into users (id, role, profile_type) values
                    ('%s', 'USER', 'STUDENT'),
                    ('%s', 'ADMIN', 'STUDENT'),
                    ('%s', 'USER', 'TEACHER')
                """.formatted(LEARNER_USER_ID, ADMIN_USER_ID, TEACHER_USER_ID));
        statement.execute("""
                insert into notes (id, owner_user_id, copied_from_note_id, source_note_id) values
                    ('%s', '%s', null, null),
                    ('%s', '%s', null, null),
                    ('%s', '%s', null, null),
                    ('%s', '%s', null, null),
                    ('%s', '%s', null, null),
                    ('%s', '%s', '%s', null),
                    ('%s', '%s', null, '%s'),
                    ('%s', '%s', null, null)
                """.formatted(
                LEARNER_NON_COPY_NOTE_ID, LEARNER_USER_ID,
                LEARNER_MULTI_ROW_NOTE_ID, LEARNER_USER_ID,
                LEARNER_NO_ROW_NOTE_ID, LEARNER_USER_ID,
                ADMIN_NOTE_ID, ADMIN_USER_ID,
                TEACHER_NOTE_ID, TEACHER_USER_ID,
                LEARNER_COPIED_FROM_NOTE_ID, LEARNER_USER_ID, TEACHER_NOTE_ID,
                LEARNER_SOURCE_NOTE_ID, LEARNER_USER_ID, TEACHER_NOTE_ID,
                ORPHAN_OWNER_NOTE_ID, MISSING_USER_ID
        ));
        // The multi-row learner note proves the DELETE assumes no cardinality: V107 creates at most one row
        // per note, but both of these must go.
        insertJoinRow(statement, LEARNER_NON_COPY_NOTE_ID);
        insertJoinRow(statement, LEARNER_MULTI_ROW_NOTE_ID);
        insertJoinRow(statement, LEARNER_MULTI_ROW_NOTE_ID);
        SURVIVING_NOTE_IDS.forEach(noteId -> insertJoinRow(statement, noteId));
    }

    private void insertJoinRow(Statement statement, String noteId) {
        try {
            statement.execute("""
                    insert into note_course_program (id, note_id, course_program_id)
                    values ('%s', '%s', '%s')
                    """.formatted(UUID.randomUUID(), noteId, UUID.randomUUID()));
        } catch (Exception exception) {
            throw new IllegalStateException("failed to seed a join row for note " + noteId, exception);
        }
    }

    private int joinRowCountFor(Statement statement, String noteId) {
        try (ResultSet resultSet = statement.executeQuery(
                "select count(*) from " + JOIN_TABLE + " where note_id = '" + noteId + "'"
        )) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to count join rows for note " + noteId, exception);
        }
    }

    private List<String> noteIdsWithJoinRows(Statement statement) throws Exception {
        List<String> noteIds = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery(
                "select distinct note_id from " + JOIN_TABLE + " order by note_id"
        )) {
            while (resultSet.next()) {
                noteIds.add(resultSet.getObject("note_id", UUID.class).toString());
            }
        }
        return noteIds;
    }

    private int count(Statement statement, String table) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("select count(*) from " + table)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }
}
