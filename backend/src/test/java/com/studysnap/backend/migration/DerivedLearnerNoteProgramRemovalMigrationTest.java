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
 *
 * <p><strong>The discriminator is the catalog name, not whether the note is a copy.</strong> An earlier
 * version of this test asserted that a row on a learner-owned COPY must survive, which encoded a bug:
 * {@code NoteService.copyNote} sets {@code source_note_id} on every copy, so that rule preserved exactly the
 * derived rows the migration exists to delete. A derived row is one whose catalog name still equals what
 * V107 would have derived from the note's own string -- that equality is the signature, and it is the exact
 * inverse of V107's insert rather than a heuristic.
 */
class DerivedLearnerNoteProgramRemovalMigrationTest {
    private static final String MIGRATION_PATH = "db/migration/V108__remove_derived_learner_note_programs.sql";
    private static final String JOIN_TABLE = "note_course_program";

    private static final String LEARNER_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ADMIN_USER_ID = "00000000-0000-0000-0000-000000000002";
    private static final String TEACHER_USER_ID = "00000000-0000-0000-0000-000000000003";
    private static final String MISSING_USER_ID = "00000000-0000-0000-0000-0000000000ff";

    private static final String NURSING_ID = "00000000-0000-0000-0000-0000000000a1";
    private static final String ACCOUNTANCY_ID = "00000000-0000-0000-0000-0000000000a2";
    private static final String EDUCATION_ID = "00000000-0000-0000-0000-0000000000a3";

    // Derived: the joined catalog name equals the note's own course_program, i.e. exactly V107's output.
    private static final String DERIVED_PLAIN_NOTE_ID = "00000000-0000-0000-0000-000000000101";
    private static final String DERIVED_ON_COPIED_FROM_NOTE_ID = "00000000-0000-0000-0000-000000000102";
    private static final String DERIVED_ON_SOURCE_COPY_NOTE_ID = "00000000-0000-0000-0000-000000000103";
    private static final String DERIVED_VIA_BSED_ALIAS_NOTE_ID = "00000000-0000-0000-0000-000000000104";
    // Legitimate: name differs from the note's string, so V107 cannot have produced it.
    private static final String INHERITED_ON_COPY_NOTE_ID = "00000000-0000-0000-0000-000000000105";
    private static final String ADMIN_NOTE_ID = "00000000-0000-0000-0000-000000000106";
    private static final String TEACHER_NOTE_ID = "00000000-0000-0000-0000-000000000107";
    private static final String ORPHAN_OWNER_NOTE_ID = "00000000-0000-0000-0000-000000000108";
    private static final String LEARNER_NO_ROW_NOTE_ID = "00000000-0000-0000-0000-000000000109";

    private static final List<String> REMOVED_NOTE_IDS = List.of(
            DERIVED_PLAIN_NOTE_ID,
            DERIVED_ON_COPIED_FROM_NOTE_ID,
            DERIVED_ON_SOURCE_COPY_NOTE_ID,
            DERIVED_VIA_BSED_ALIAS_NOTE_ID
    );
    private static final List<String> SURVIVING_NOTE_IDS = List.of(
            INHERITED_ON_COPY_NOTE_ID,
            ADMIN_NOTE_ID,
            TEACHER_NOTE_ID,
            ORPHAN_OWNER_NOTE_ID
    );

    @Test
    void migrationRemovesOnlyMechanicallyDerivedLearnerRows() throws Exception {
        String databaseName = "derived-learner-note-program-removal-" + UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL")) {
            try (Statement statement = connection.createStatement()) {
                createSchema(statement);
                seedFixtures(statement);

                assertThat(count(statement, JOIN_TABLE)).isEqualTo(8);

                String migration = strippedMigration();
                statement.execute(migration);

                // Every row V107 derived from a learner's own string is gone -- INCLUDING the ones on copies,
                // which the previous predicate wrongly preserved because copyNote sets source_note_id on
                // every copy. That is the finding which made the migration a no-op for the copy population.
                REMOVED_NOTE_IDS.forEach(noteId -> assertThat(joinRowCountFor(statement, noteId))
                        .as("derived row on learner-owned note %s", noteId)
                        .isZero());
                // ...and everything with legitimate provenance survives. Asserting survivors, not only
                // deletions, is what makes an over-broad DELETE fail instead of passing.
                SURVIVING_NOTE_IDS.forEach(noteId -> assertThat(joinRowCountFor(statement, noteId))
                        .as("legitimately provenanced row on note %s must survive", noteId)
                        .isEqualTo(1));
                assertThat(count(statement, JOIN_TABLE)).isEqualTo(4);
                assertThat(noteIdsWithJoinRows(statement)).containsExactlyInAnyOrderElementsOf(SURVIVING_NOTE_IDS);

                assertThat(joinRowCountFor(statement, LEARNER_NO_ROW_NOTE_ID)).isZero();
                assertThat(count(statement, "notes")).isEqualTo(9);

                // Re-running is a no-op.
                statement.execute(migration);
                assertThat(count(statement, JOIN_TABLE)).isEqualTo(4);
                assertThat(noteIdsWithJoinRows(statement)).containsExactlyInAnyOrderElementsOf(SURVIVING_NOTE_IDS);
            }
        }
    }

    /**
     * A note carrying BOTH a derived and an inherited row must lose only the derived one. This is why the
     * DELETE targets {@code note_course_program.id} rather than {@code note_id}.
     */
    @Test
    void migrationRemovesTheDerivedRowWithoutTakingAnInheritedOneOnTheSameNote() throws Exception {
        String databaseName = "derived-learner-mixed-" + UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL")) {
            try (Statement statement = connection.createStatement()) {
                createSchema(statement);
                seedUsersAndCatalog(statement);
                insertNote(statement, DERIVED_ON_COPIED_FROM_NOTE_ID, LEARNER_USER_ID, "Nursing", ADMIN_NOTE_ID, null);
                insertJoinRow(statement, DERIVED_ON_COPIED_FROM_NOTE_ID, NURSING_ID);      // derived
                insertJoinRow(statement, DERIVED_ON_COPIED_FROM_NOTE_ID, ACCOUNTANCY_ID);  // inherited

                statement.execute(strippedMigration());

                assertThat(joinRowCountFor(statement, DERIVED_ON_COPIED_FROM_NOTE_ID)).isEqualTo(1);
                assertThat(remainingProgramIdsFor(statement, DERIVED_ON_COPIED_FROM_NOTE_ID))
                        .containsExactly(ACCOUNTANCY_ID);
            }
        }
    }

    /**
     * Structural guard against the trap documented in pressure-test finding B4: {@code V107}'s backfill sits
     * inside its PL/pgSQL block, which this strip helper removes, so nothing tests it. If someone later moves
     * {@code V108}'s DELETE into its reporting block, this test would keep passing while asserting nothing.
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
     * {@code RAISE NOTICE} counts for the deploy log and asserts nothing. Mirrors
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
                create table course_programs (
                    id uuid primary key,
                    name varchar(120) not null
                )
                """);
        statement.execute("""
                create table notes (
                    id uuid primary key,
                    owner_user_id uuid not null,
                    course_program varchar(120),
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

    private void seedUsersAndCatalog(Statement statement) throws Exception {
        // The owner fixtures discriminate on ONE attribute each, so dropping either half of the owner
        // condition fails a test: the admin is not a teacher, the teacher is not an admin.
        statement.execute("""
                insert into users (id, role, profile_type) values
                    ('%s', 'USER', 'STUDENT'),
                    ('%s', 'ADMIN', 'STUDENT'),
                    ('%s', 'USER', 'TEACHER')
                """.formatted(LEARNER_USER_ID, ADMIN_USER_ID, TEACHER_USER_ID));
        statement.execute("""
                insert into course_programs (id, name) values
                    ('%s', 'Nursing'),
                    ('%s', 'Accountancy'),
                    ('%s', 'Education')
                """.formatted(NURSING_ID, ACCOUNTANCY_ID, EDUCATION_ID));
    }

    private void seedFixtures(Statement statement) throws Exception {
        seedUsersAndCatalog(statement);

        // Derived: joined name == the note's own course_program, i.e. exactly V107's output.
        insertNote(statement, DERIVED_PLAIN_NOTE_ID, LEARNER_USER_ID, "Nursing", null, null);
        insertJoinRow(statement, DERIVED_PLAIN_NOTE_ID, NURSING_ID);
        // Both copy shapes. These are the F1 regression: copyNote sets source_note_id on EVERY copy, so a
        // predicate keyed on "is a copy" preserved these derived rows permanently.
        insertNote(statement, DERIVED_ON_COPIED_FROM_NOTE_ID, LEARNER_USER_ID, "Nursing", ADMIN_NOTE_ID, null);
        insertJoinRow(statement, DERIVED_ON_COPIED_FROM_NOTE_ID, NURSING_ID);
        insertNote(statement, DERIVED_ON_SOURCE_COPY_NOTE_ID, LEARNER_USER_ID, "Nursing", null, ADMIN_NOTE_ID);
        insertJoinRow(statement, DERIVED_ON_SOURCE_COPY_NOTE_ID, NURSING_ID);
        // V107's sole alias: 'Bsed' was backfilled to the 'Education' catalog row, so the names differ and a
        // naive equality check would miss it.
        insertNote(statement, DERIVED_VIA_BSED_ALIAS_NOTE_ID, LEARNER_USER_ID, "Bsed", null, null);
        insertJoinRow(statement, DERIVED_VIA_BSED_ALIAS_NOTE_ID, EDUCATION_ID);

        // Legitimate: the joined name differs from the note's string, so V107 cannot have produced it.
        insertNote(statement, INHERITED_ON_COPY_NOTE_ID, LEARNER_USER_ID, "Nursing", null, ADMIN_NOTE_ID);
        insertJoinRow(statement, INHERITED_ON_COPY_NOTE_ID, ACCOUNTANCY_ID);
        insertNote(statement, ADMIN_NOTE_ID, ADMIN_USER_ID, null, null, null);
        insertJoinRow(statement, ADMIN_NOTE_ID, NURSING_ID);
        insertNote(statement, TEACHER_NOTE_ID, TEACHER_USER_ID, null, null, null);
        insertJoinRow(statement, TEACHER_NOTE_ID, NURSING_ID);
        insertNote(statement, ORPHAN_OWNER_NOTE_ID, MISSING_USER_ID, "Nursing", null, null);
        insertJoinRow(statement, ORPHAN_OWNER_NOTE_ID, NURSING_ID);

        insertNote(statement, LEARNER_NO_ROW_NOTE_ID, LEARNER_USER_ID, "Nursing", null, null);
    }

    private void insertNote(
            Statement statement,
            String noteId,
            String ownerId,
            String courseProgram,
            String copiedFromNoteId,
            String sourceNoteId
    ) {
        try {
            statement.execute("""
                    insert into notes (id, owner_user_id, course_program, copied_from_note_id, source_note_id)
                    values ('%s', '%s', %s, %s, %s)
                    """.formatted(
                    noteId,
                    ownerId,
                    quoteOrNull(courseProgram),
                    quoteOrNull(copiedFromNoteId),
                    quoteOrNull(sourceNoteId)
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("failed to seed note " + noteId, exception);
        }
    }

    private String quoteOrNull(String value) {
        return value == null ? "null" : "'" + value + "'";
    }

    private void insertJoinRow(Statement statement, String noteId, String courseProgramId) {
        try {
            statement.execute("""
                    insert into note_course_program (id, note_id, course_program_id)
                    values ('%s', '%s', '%s')
                    """.formatted(UUID.randomUUID(), noteId, courseProgramId));
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

    private List<String> remainingProgramIdsFor(Statement statement, String noteId) throws Exception {
        List<String> ids = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery(
                "select course_program_id from " + JOIN_TABLE + " where note_id = '" + noteId + "'"
        )) {
            while (resultSet.next()) {
                ids.add(resultSet.getObject("course_program_id", UUID.class).toString());
            }
        }
        return ids;
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
