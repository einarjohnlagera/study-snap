package com.studysnap.backend.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class ProgramFamilyInitialMembershipMigrationTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V147__program_family_initial_membership.sql");
    private static final Pattern MATRIX_ROW = Pattern.compile(
            "\\('([^']+)'(?:::\\w+)?,\\s*'([^']+)'(?:::\\w+)?\\)");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    private String migrationSql;
    private List<Pair> approved;

    @BeforeEach
    void resetSchema() throws Exception {
        migrationSql = Files.readString(MIGRATION);
        Matcher matcher = MATRIX_ROW.matcher(migrationSql);
        var pairs = new java.util.ArrayList<Pair>();
        while (matcher.find()) pairs.add(new Pair(matcher.group(1), matcher.group(2)));
        approved = List.copyOf(pairs);
        assertThat(approved).hasSize(50);

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS course_program_family, course_programs, program_families CASCADE");
            statement.execute("""
                    CREATE TABLE program_families (
                        id uuid PRIMARY KEY, name varchar(120) NOT NULL UNIQUE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE course_programs (
                        id uuid PRIMARY KEY, name varchar(120) NOT NULL UNIQUE,
                        program_family_id uuid NULL, is_active boolean NOT NULL DEFAULT true
                    )
                    """);
            statement.execute("""
                    CREATE TABLE course_program_family (
                        id uuid PRIMARY KEY, course_program_id uuid NOT NULL REFERENCES course_programs(id),
                        program_family_id uuid NOT NULL REFERENCES program_families(id),
                        CONSTRAINT uk_course_program_family_program_family
                            UNIQUE (course_program_id, program_family_id)
                    )
                    """);
        }
    }

    @Test
    void landsAllFiftyApprovedPairsAndIsIdempotent() throws Exception {
        seedMatrixFixture(Set.of());
        Snapshot before = snapshot();

        runMigration();
        assertApprovedSets();
        assertThat(membershipCount()).isEqualTo(50);
        assertCatalogAndLegacyColumnsUnchanged(before);

        runMigration();
        assertThat(membershipCount()).isEqualTo(50);
        assertCatalogAndLegacyColumnsUnchanged(before);
    }

    @Test
    void preservesAnOffMatrixMembership() throws Exception {
        seedMatrixFixture(Set.of());
        UUID lawId = insertProgram("Law", UUID.randomUUID());
        UUID engineeringId = familyId("Engineering");
        insertMembership(lawId, engineeringId);
        Snapshot before = snapshot();

        runMigration();

        assertThat(membershipCount()).isEqualTo(51);
        assertThat(hasMembership("Law", "Engineering")).isTrue();
        assertCatalogAndLegacyColumnsUnchanged(before);
    }

    @Test
    void skipsAMissingCanonicalRowAndLandsTheOtherFortyNinePairs() throws Exception {
        seedMatrixFixture(Set.of("Pharmacy"));
        Snapshot before = snapshot();

        runMigration();

        assertThat(membershipCount()).isEqualTo(49);
        assertThat(hasMembership("Pharmacy", "Health Sciences")).isFalse();
        assertCatalogAndLegacyColumnsUnchanged(before);
    }

    private void seedMatrixFixture(Set<String> omittedPrograms) throws Exception {
        approved.stream().map(Pair::family).distinct().forEach(this::insertFamilyUnchecked);
        approved.stream().map(Pair::program).distinct()
                .filter(name -> !omittedPrograms.contains(name))
                .forEach(name -> insertProgramUnchecked(name, UUID.randomUUID()));
        assertThat(count("program_families")).isEqualTo(6);
        assertThat(count("course_programs")).isEqualTo(44 - omittedPrograms.size());
    }

    private void assertApprovedSets() throws Exception {
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        approved.forEach(pair -> expected.computeIfAbsent(pair.family(), ignored -> new LinkedHashSet<>())
                .add(pair.program()));
        Map<String, Set<String>> actual = new LinkedHashMap<>();
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT pf.name family_name, cp.name program_name
                     FROM course_program_family cpf
                     JOIN program_families pf ON pf.id = cpf.program_family_id
                     JOIN course_programs cp ON cp.id = cpf.course_program_id
                     ORDER BY pf.name, cp.name
                     """)) {
            while (rows.next()) {
                actual.computeIfAbsent(rows.getString("family_name"), ignored -> new LinkedHashSet<>())
                        .add(rows.getString("program_name"));
            }
        }
        assertThat(actual).isEqualTo(expected);
    }

    private void assertCatalogAndLegacyColumnsUnchanged(Snapshot before) throws Exception {
        Snapshot after = snapshot();
        assertThat(after.familyCount()).isEqualTo(before.familyCount());
        assertThat(after.programCount()).isEqualTo(before.programCount());
        assertThat(after.legacyFamilyIds()).isEqualTo(before.legacyFamilyIds());
    }

    private Snapshot snapshot() throws Exception {
        Map<String, UUID> legacy = new LinkedHashMap<>();
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT name, program_family_id FROM course_programs ORDER BY name")) {
            while (rows.next()) legacy.put(rows.getString("name"), rows.getObject("program_family_id", UUID.class));
        }
        return new Snapshot(count("program_families"), count("course_programs"), legacy);
    }

    private void runMigration() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(migrationSql);
        }
    }

    private boolean hasMembership(String program, String family) throws Exception {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1 FROM course_program_family cpf
                    JOIN course_programs cp ON cp.id = cpf.course_program_id
                    JOIN program_families pf ON pf.id = cpf.program_family_id
                    WHERE cp.name = ? AND pf.name = ?
                )
                """)) {
            statement.setString(1, program);
            statement.setString(2, family);
            try (ResultSet row = statement.executeQuery()) { row.next(); return row.getBoolean(1); }
        }
    }

    private int membershipCount() throws Exception { return count("course_program_family"); }

    private int count(String table) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT count(*) FROM " + table)) {
            row.next();
            return row.getInt(1);
        }
    }

    private UUID familyId(String name) throws Exception {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM program_families WHERE name = ?")) {
            statement.setString(1, name);
            try (ResultSet row = statement.executeQuery()) { row.next(); return row.getObject(1, UUID.class); }
        }
    }

    private void insertFamilyUnchecked(String name) {
        try { insertFamily(name); } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private void insertProgramUnchecked(String name, UUID legacyFamilyId) {
        try { insertProgram(name, legacyFamilyId); } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private UUID insertFamily(String name) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO program_families (id, name) VALUES (?, ?)")) {
            statement.setObject(1, id); statement.setString(2, name); statement.executeUpdate();
        }
        return id;
    }

    private UUID insertProgram(String name, UUID legacyFamilyId) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO course_programs (id, name, program_family_id) VALUES (?, ?, ?)")) {
            statement.setObject(1, id); statement.setString(2, name); statement.setObject(3, legacyFamilyId);
            statement.executeUpdate();
        }
        return id;
    }

    private void insertMembership(UUID programId, UUID familyId) throws Exception {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO course_program_family (id, course_program_id, program_family_id) VALUES (?, ?, ?)")) {
            statement.setObject(1, UUID.randomUUID()); statement.setObject(2, programId);
            statement.setObject(3, familyId); statement.executeUpdate();
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private record Pair(String family, String program) { }
    private record Snapshot(int familyCount, int programCount, Map<String, UUID> legacyFamilyIds) { }
}
