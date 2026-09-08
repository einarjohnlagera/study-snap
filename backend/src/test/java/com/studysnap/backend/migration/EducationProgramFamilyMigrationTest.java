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
 * Guards {@code V142__education_program_family.sql}.
 *
 * <p>⚠️ THE FEATURE IS THIS MIGRATION. There is no Engineering-specific authoring shortcut to
 * generalize: {@code applicable-programs-combobox.tsx} derives its families dynamically from the
 * catalog and contains no Engineering literal, so seeding the Education family is what makes the UI
 * render "Add all N Education programs". If a future change makes this migration a no-op, the feature
 * silently disappears with no compile error and no other failing test.
 *
 * <p>⚠️ THE RENAME GUARD'S FIXTURE IS CREATED BEFORE THE MIGRATION RUNS, DELIBERATELY. A
 * {@code note_course_program} row inserted after the rename would resolve to the new name trivially
 * and prove nothing about whether the rename preserved anything.
 *
 * <p>⚠️ ITS PRODUCTION DENOMINATOR IS ZERO — a 2026-09-08 read found no note linked to the renamed
 * row. This is therefore a structural guard, and must not be reported as evidence that real data
 * survived the rename.
 */
class EducationProgramFamilyMigrationTest {

    private static final String EDUCATION_FAMILY_ID = "10000000-0000-0000-0000-000000000002";
    private static final String ENGINEERING_FAMILY_ID = "10000000-0000-0000-0000-000000000001";
    private static final String EXISTING_EDUCATION_ID = "20000000-0000-0000-0000-000000000001";
    private static final String SPECIAL_NEEDS_ID = "20000000-0000-0000-0000-000000000021";
    private static final String CIVIL_ENGINEERING_ID = "20000000-0000-0000-0000-000000000005";
    private static final String COURSE_PROGRAMS = "course_programs";
    private static final String PROGRAM_FAMILIES = "program_families";

    @Test
    void v142SeedsTheEducationFamilyAndRenamesWithoutStrandingAnything() throws Exception {
        String databaseName = "education-program-family-migration-" + UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL");
             Statement statement = connection.createStatement()) {

            statement.execute("create table notes (id uuid primary key, course_program varchar(120))");
            statement.execute("create table users (id uuid primary key, course_program varchar(120))");
            applyMigration(statement, "V106__course_program_catalog.sql");

            // ⚠️ THE RENAME GUARD'S FIXTURE, CREATED BEFORE V142 RUNS. note_course_program joins by
            // course_program_id, so this row is what proves the rename kept the row's identity rather
            // than replacing it.
            statement.execute("create table note_course_program ("
                    + "note_id uuid, course_program_id uuid, primary key (note_id, course_program_id))");
            UUID linkedNoteId = UUID.randomUUID();
            statement.execute("insert into note_course_program (note_id, course_program_id) values ('"
                    + linkedNoteId + "', '" + SPECIAL_NEEDS_ID + "')");

            assertThat(count(statement, PROGRAM_FAMILIES)).isEqualTo(1);
            assertThat(count(statement, COURSE_PROGRAMS)).isEqualTo(21);

            applyMigration(statement, "V142__education_program_family.sql");

            // 1. The family exists and Engineering is untouched beside it.
            assertThat(count(statement, PROGRAM_FAMILIES)).isEqualTo(2);
            assertThat(names(statement, "select name from program_families order by name"))
                    .containsExactly("Education", "Engineering");

            // 2. The EXISTING Education row is assigned, never replaced, and keeps its exam goal.
            assertThat(single(statement,
                    "select program_family_id from course_programs where id = '" + EXISTING_EDUCATION_ID + "'"))
                    .isEqualToIgnoringCase(EDUCATION_FAMILY_ID);
            assertThat(single(statement,
                    "select exam_goal_slug from course_programs where id = '" + EXISTING_EDUCATION_ID + "'"))
                    .isEqualTo("let");
            assertThat(single(statement,
                    "select name from course_programs where id = '" + EXISTING_EDUCATION_ID + "'"))
                    .isEqualTo("Education");

            // 3. The rename kept the row's id, so the pre-existing link still resolves -- and now
            //    resolves to the NEW name.
            assertThat(single(statement,
                    "select name from course_programs where id = '" + SPECIAL_NEEDS_ID + "'"))
                    .isEqualTo("Special Needs Education");
            assertThat(single(statement, "select cp.name from note_course_program ncp"
                    + " join course_programs cp on cp.id = ncp.course_program_id"
                    + " where ncp.note_id = '" + linkedNoteId + "'"))
                    .isEqualTo("Special Needs Education");
            assertThat(count(statement, "note_course_program"))
                    .as("the rename must not drop the join row")
                    .isEqualTo(1);

            // ⚠️ NO DUPLICATE. The whole point of renaming rather than inserting is that
            // 'Special Needs Education – Generalist' stops existing instead of gaining a sibling.
            assertThat(names(statement,
                    "select name from course_programs where name like 'Special Needs Education%' order by name"))
                    .containsExactly("Special Needs Education");

            // 4. The six new programs, all in the family, all carrying the LET goal per the owner's rule.
            assertThat(count(statement, COURSE_PROGRAMS)).isEqualTo(27);
            assertThat(names(statement, "select name from course_programs where program_family_id = '"
                    + EDUCATION_FAMILY_ID + "' order by name"))
                    .containsExactly(
                            "Early Childhood Education",
                            "Education",
                            "Elementary Education",
                            "Physical Education",
                            "Secondary Education",
                            "Special Needs Education",
                            "Teacher Certification",
                            "Technical-Vocational Teacher Education");
            assertThat(count(statement, "course_programs where program_family_id = '"
                    + EDUCATION_FAMILY_ID + "' and exam_goal_slug = 'let'"))
                    .as("every Education-family program leads to the LET")
                    .isEqualTo(8);

            // 5. ⚠️ THE MIGRATION'S REGRESSION GUARD: assigning a family to the Education row must not
            //    have touched Engineering's members. This is what catches an over-broad UPDATE.
            assertThat(count(statement, "course_programs where program_family_id = '"
                    + ENGINEERING_FAMILY_ID + "'")).isEqualTo(3);
            assertThat(single(statement,
                    "select program_family_id from course_programs where id = '" + CIVIL_ENGINEERING_ID + "'"))
                    .isEqualToIgnoringCase(ENGINEERING_FAMILY_ID);

            // 6. No credential abbreviation leaked in as a canonical name, and 'Professional Education'
            //    was not used -- it already exists as a DomainContext value AND as a Subject.
            assertThat(names(statement, "select name from course_programs order by name"))
                    .doesNotContain("BEEd", "BSEd", "BPEd", "CPE", "DPE", "Professional Education");
        }
    }

    private void applyMigration(Statement statement, String file) throws Exception {
        String migration = new ClassPathResource("db/migration/" + file)
                .getContentAsString(StandardCharsets.UTF_8);
        statement.execute(stripInformationalPlPgSqlBlock(migration));
    }

    /**
     * V106 ends with an informational {@code DO $$} block that H2 cannot parse. It asserts nothing --
     * it only emits {@code RAISE NOTICE} deploy-log counts -- so dropping it costs this test no
     * coverage. {@code CourseProgramCatalogMigrationTest} strips it the same way and separately guards
     * that the block still exists in the shipped file.
     */
    private String stripInformationalPlPgSqlBlock(String migration) {
        int blockStart = migration.indexOf("DO $$");
        return blockStart < 0 ? migration : migration.substring(0, blockStart);
    }

    private int count(Statement statement, String from) throws Exception {
        try (ResultSet rs = statement.executeQuery("select count(*) from " + from)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private String single(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private List<String> names(Statement statement, String sql) throws Exception {
        List<String> values = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
    }
}
